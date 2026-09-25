package git.shin.komorei.sdk

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.remote.HEADER_X_REQUESTED_WITH
import git.shin.komorei.sdk.runner.HostHttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host-level tests for the JS-challenge bypass — a 403..503 page carrying
 * challenge markers goes through the headless WebView solve, falls back to the
 * visible-browser dialog when a human is needed ([JsChallengeCoordinator]), and
 * only then retries with the freshly-set cookies.
 *
 * The short-circuit OkHttp interceptor plays scripted responses (no real
 * network); the headless WebView state is driven through the existing
 * `jsEvalOverride` + `onChallengeWebViewCreated` test seams.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KrxHostChallengeTest {
    private val calls = AtomicInteger(0)

    @Before
    fun setUp() {
        calls.set(0)
        JsChallengeCoordinator.resetForTest()
    }

    @After
    fun tearDown() {
        JsChallengeCoordinator.resetForTest()
    }

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** A Cloudflare-style challenge page (markers that must trigger the solve). */
    private fun challengeHtml(): String =
        "<html><head><title>Just a moment...</title></head>" +
            "<body><span class=\"cf-challenge\">Checking your browser before accessing</span></body></html>"

    private fun response(
        request: okhttp3.Request,
        code: Int,
        message: String,
        body: String,
        contentType: String,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .header("Content-Type", contentType)
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()

    /** Client whose interceptor plays the Nth response from a script. */
    private fun scriptedClient(script: (call: Int, request: okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                script(calls.incrementAndGet(), chain.request())
            }.build()

    private fun get(
        host: KrxHostImpl,
        url: String = "https://example.com/api",
    ) = host.netRequest(HostHttpMethod.GET, url, emptyMap(), byteArrayOf(), null)

    // ---- plain errors are NOT challenges -----------------------------------

    @Test
    fun `plain 403 without challenge markers is returned as-is and not retried`() {
        val host =
            KrxHostImpl(
                context(),
                scriptedClient { _, request -> response(request, 403, "Forbidden", "<html><body>Access denied</body></html>", "text/html") },
            )
        val resp = get(host)
        assertEquals(403, resp.status)
        assertEquals("single attempt — a bare 403 is not a challenge", 1, calls.get())
        assertNull("no user-dialog was requested", JsChallengeCoordinator.pending.value)
    }

    @Test
    fun `challenge markers on a 200 or non-html body are not treated as challenges`() {
        val host =
            KrxHostImpl(
                context(),
                scriptedClient { _, request -> response(request, 200, "OK", challengeHtml(), "text/html") },
            )
        val resp = get(host)
        assertEquals(200, resp.status)
        assertEquals(1, calls.get())
    }

    // ---- headless solve ----------------------------------------------------

    @Test
    fun `403 challenge is solved in a headless webview then retried`() {
        var capturedView: WebView? = null
        val host =
            KrxHostImpl(
                context(),
                scriptedClient { call, request ->
                    if (call == 1) {
                        response(request, 403, "Forbidden", challengeHtml(), "text/html")
                    } else {
                        response(request, 200, "OK", "{\"ok\":true}", "application/json")
                    }
                },
            )
        host.jsEvalOverride = { "false" } // the challenge document already cleared
        host.onChallengeWebViewCreated = { capturedView = it }

        val resp = get(host)

        assertEquals(200, resp.status)
        assertEquals("initial 403 + challenge-retry", 2, calls.get())

        // The headless WebView must have loaded the URL with the Android
        // WebView fingerprint neutralized (X-Requested-With emptied).
        val view = requireNotNull(capturedView) { "headless challenge WebView must be created" }
        val headers = shadowOf(view).lastAdditionalHttpHeaders
        assertEquals("X-Requested-With must be emptied on the challenge load", "", headers[HEADER_X_REQUESTED_WITH])
    }

    // ---- headless stall → visible browser ----------------------------------

    @Test
    fun `headless stall falls back to the visible browser and retries after the user completes`() {
        val sawPending = AtomicBoolean(false)
        val host =
            KrxHostImpl(
                context(),
                scriptedClient { call, request ->
                    if (call == 1) {
                        response(request, 403, "Forbidden", challengeHtml(), "text/html")
                    } else {
                        response(request, 200, "OK", "ok", "text/plain")
                    }
                },
            )
        host.jsEvalOverride = { "true" } // the challenge never clears headlessly
        host.challengeSolveTimeoutMs = 50
        host.challengePollIntervalMs = 10

        val watcher =
            Thread {
                // Collect the flow rather than polling `pending.value` on a
                // timer: the pending window is short, so a 2ms poll could
                // straddle "published" and "consumed" and miss the request
                // entirely — which is exactly how this test used to fail
                // intermittently on a loaded machine.
                runBlocking {
                    runCatching {
                        withTimeout(WATCH_TIMEOUT_MS) {
                            JsChallengeCoordinator.pending.first { it != null }
                        }
                    }
                }.onSuccess { sawPending.set(true) }
            }
        watcher.start()
        val completer =
            Thread {
                Thread.sleep(120)
                JsChallengeCoordinator.completeBypass()
            }
        completer.start()

        val resp = get(host)

        completer.join(2_000)
        watcher.join(2_000)

        assertTrue("the visible-browser dialog must have been requested", sawPending.get())
        assertEquals(200, resp.status)
        assertEquals("initial 403 + bypass-retry", 2, calls.get())
        assertNull("the coordinator must be idle afterwards", JsChallengeCoordinator.pending.value)
    }

    @Test
    fun `cancelling the visible browser returns the 403 without retry`() {
        val host =
            KrxHostImpl(
                context(),
                scriptedClient { _, request -> response(request, 403, "Forbidden", challengeHtml(), "text/html") },
            )
        host.jsEvalOverride = { "true" }
        host.challengeSolveTimeoutMs = 50
        host.challengePollIntervalMs = 10

        val canceller =
            Thread {
                Thread.sleep(100)
                JsChallengeCoordinator.cancelBypass()
            }
        canceller.start()

        val resp = get(host)

        canceller.join(2_000)

        assertEquals(403, resp.status)
        assertEquals("no retry after the user cancelled", 1, calls.get())
        assertNull(JsChallengeCoordinator.pending.value)
    }

    private companion object {
        /** How long the watcher waits for the coordinator to publish `pending`. */
        const val WATCH_TIMEOUT_MS = 5_000L
    }
}
