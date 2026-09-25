package git.shin.komorei.sdk

import android.util.Log
import git.shin.komorei.sdk.runner.KomoreiRunner
import org.json.JSONObject
import java.util.zip.ZipInputStream

/**
 * Lightweight representation of the `source.json` manifest inside a `.krx` archive.
 */
data class KrxSourceMeta(
    val id: String,
    val name: String,
    val version: Int,
    val url: String,
    val languages: List<String>,
    val contentRating: Int,
)

/**
 * Loads `.krx` source packages for the embedded runner.
 *
 * A `.krx` is a zip archive (aidoku convention) whose wasm payload lives at
 * `Payload/main.wasm`. [load] extracts it, creates the runner bound to a real
 * [KrxHostImpl] and runs the source `start()` export — exactly like an app would
 * install a source. The returned [KomoreiRunner] (an `AutoCloseable`) is the
 * handle for all source calls (search / details / streams / filters / ...).
 */
object KrxManager {
    private const val TAG = "KrxManager"

    /** The wasm payload path inside a `.krx` archive. */
    const val MAIN_WASM_ENTRY = "Payload/main.wasm"

    /** Icon entry names inside a `.krx` archive (aidoku root + payload-scoped). */
    private val ICON_ENTRIES = setOf("icon.png", "Payload/icon.png")

    /**
     * Extracts `Payload/main.wasm` from a `.krx` archive. Returns null when the
     * entry is missing or the archive is invalid.
     */
    fun extractMainWasm(krx: ByteArray): ByteArray? {
        ZipInputStream(krx.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == MAIN_WASM_ENTRY) {
                    return zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /**
     * Extracts the source brand icon (`icon.png` / `Payload/icon.png`) from a
     * `.krx` archive. Returns null when the package ships no icon.
     */
    fun extractIcon(krx: ByteArray): ByteArray? {
        ZipInputStream(krx.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name in ICON_ENTRIES) {
                    return zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /**
     * Reads `source.json` from a `.krx` archive and parses the manifest.
     * Returns null when the entry is missing or malformed.
     *
     * Both packaging layouts are accepted: the aidoku convention puts the
     * manifest at the archive ROOT (`source.json`), while payload-scoped
     * packages keep it next to the wasm (`Payload/source.json`).
     */
    fun readInfo(krx: ByteArray): KrxSourceMeta? {
        ZipInputStream(krx.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "source.json" || name == "Payload/source.json") {
                    return try {
                        val json = zip.readBytes().toString(Charsets.UTF_8)
                        val info = JSONObject(json).getJSONObject("info")
                        KrxSourceMeta(
                            id = info.getString("id"),
                            name = info.getString("name"),
                            version = info.optInt("version", 1),
                            url = info.optString("url", ""),
                            languages =
                                info.optJSONArray("languages")?.let { arr ->
                                    (0 until arr.length()).map { arr.getString(it) }
                                } ?: emptyList(),
                            contentRating = info.optInt("contentRating", 0),
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse source.json", e)
                        null
                    }
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /**
     * Instantiates a runner for the given `.krx` bytes and runs its `start()`.
     * Throws [IllegalArgumentException] when the package has no wasm payload.
     *
     * Callers should run this off the main thread (the runner is synchronous and
     * its host performs blocking IO on every call).
     */
    fun load(
        host: KrxHostImpl,
        krx: ByteArray,
    ): KomoreiRunner {
        val wasm =
            extractMainWasm(krx)
                ?: throw IllegalArgumentException("Invalid .krx package: missing $MAIN_WASM_ENTRY")
        val runner = KomoreiRunner(host)
        try {
            runner.load(wasm)
            runner.start()
        } catch (e: Exception) {
            runner.close()
            throw e
        }
        return runner
    }
}
