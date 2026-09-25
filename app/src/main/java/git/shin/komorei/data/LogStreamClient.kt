package git.shin.komorei.data

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional remote log sink used by the Komorei/Aidoku `logcat` workflow.
 *
 * The CLI command `komorei logcat --port 9000` exposes a POST endpoint on `/`.
 * Each new local log line is sent asynchronously so the wasm runner thread is
 * never blocked by a disconnected development machine.
 */
object LogStreamClient {
    private const val PREFERENCES = "komorei_log_stream"
    private const val KEY_SERVER_URL = "server_url"
    private const val LOG_MIME_TYPE = "text/plain; charset=utf-8"
    private const val MAX_QUEUED_LOGS = 128

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var streamUrl: HttpUrl? = null

    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()

    private val executor =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(MAX_QUEUED_LOGS),
            ThreadFactory { runnable ->
                Thread(runnable, "komorei-log-stream").apply { isDaemon = true }
            },
            ThreadPoolExecutor.DiscardOldestPolicy(),
        )

    /** Restores the persisted URL once during application startup. */
    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        streamUrl =
            parse(
                appContext
                    .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(KEY_SERVER_URL, null),
            )
    }

    /** Current normalized URL, or an empty string when remote logging is disabled. */
    fun currentUrl(context: Context): String {
        initialize(context)
        return streamUrl?.toString().orEmpty()
    }

    /**
     * Validates and persists the log server URL.
     *
     * @return `true` when streaming is enabled, `false` when it was cleared.
     */
    fun setUrl(
        context: Context,
        rawValue: String,
    ): Result<Boolean> {
        initialize(context)
        val value = rawValue.trim()
        val parsed =
            if (value.isEmpty()) {
                null
            } else {
                parse(value)
                    ?: return Result.failure(IllegalArgumentException("Invalid log server URL"))
            }

        context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, parsed?.toString().orEmpty())
            .apply()
        streamUrl = parsed
        return Result.success(parsed != null)
    }

    /** Queues one already formatted log line for best-effort delivery. */
    fun send(formattedLine: String) {
        val url = streamUrl ?: return
        runCatching {
            executor.execute {
                runCatching {
                    val body = formattedLine.toRequestBody(LOG_MIME_TYPE.toMediaType())
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("User-Agent", "Komorei-LogStream/1")
                            .post(body)
                            .build()
                    httpClient.newCall(request).execute().use { response ->
                        // The CLI only needs a successful POST; failed requests
                        // are intentionally silent so logging cannot recurse.
                        response.body.close()
                    }
                }
            }
        }
    }

    private fun parse(rawValue: String?): HttpUrl? {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return null
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.scheme !in setOf("http", "https")) return null
        if (url.host.isBlank() || url.encodedPath != "/" || url.query != null || url.fragment != null) {
            return null
        }
        return url
    }
}
