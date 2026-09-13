package git.shin.komorei.sdk

import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit-test the real Kotlin js host ([KrxHostImpl] `js_*` methods) — JS
 * contexts/values and webviews backed by Android WebView.
 *
 * Robolectric's paused main looper can never deliver the async
 * `evaluateJavascript` callback, so eval results come from [KrxHostImpl.jsEvalOverride]
 * (canned raw WebView-style JSON strings) and flow through the same
 * interpretation pipeline as production. Page loads / rules / user scripts /
 * cookies drive the real WebView shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KrxHostJsTest {

    private lateinit var host: KrxHostImpl

    @Before
    fun setUp() {
        host = KrxHostImpl(ApplicationProvider.getApplicationContext<android.content.Context>())
    }

    @After
    fun tearDown() {
        host.jsEvalOverride = null
    }

    // ---- contexts ----------------------------------------------------------

    @Test
    fun `js context eval returns string value handle`() {
        val context = host.jsContextCreate()
        assertTrue("context handle must be positive", context > 0)

        host.jsEvalOverride = { "\"hello\"" }
        val value = host.jsContextEval(context, "'hello'")
        assertTrue("value handle must be positive", value > 0)

        assertEquals("hello", host.jsValueToString(value))
        assertTrue(host.jsValueIsDefined(value))
        assertFalse(host.jsValueIsNull(value))
    }

    @Test
    fun `js value conversions follow js semantics`() {
        val context = host.jsContextCreate()

        host.jsEvalOverride = { "4" }
        val number = host.jsContextEval(context, "2+2")
        assertEquals(4, host.jsValueToInt(number))
        assertEquals(4.0, host.jsValueToF64(number), 0.0)
        assertTrue(host.jsValueToBool(number))
        assertEquals("4", host.jsValueToString(number))

        host.jsEvalOverride = { "\"4px\"" }
        val string = host.jsContextEval(context, "'4px'")
        assertEquals("4px", host.jsValueToString(string))
        assertTrue(host.jsValueToBool(string))
        assertEquals(0, host.jsValueToInt(string)) // non-numeric string -> 0

        host.jsEvalOverride = { "undefined" }
        val undef = host.jsContextEval(context, "void 0")
        assertFalse(host.jsValueIsDefined(undef))
        assertNull(host.jsValueToString(undef))
        assertFalse(host.jsValueToBool(undef))

        host.jsEvalOverride = { "null" }
        val nul = host.jsContextEval(context, "null")
        assertTrue(host.jsValueIsNull(nul))
        assertTrue(host.jsValueIsDefined(nul))
        assertEquals("null", host.jsValueToString(nul))
        assertFalse(host.jsValueToBool(nul))
    }

    @Test
    fun `js context get reads a global property`() {
        val context = host.jsContextCreate()
        val scripts = mutableListOf<String>()
        host.jsEvalOverride = { script ->
            scripts.add(script)
            "\"global-foo\""
        }

        val value = host.jsContextGet(context, "foo")
        assertEquals("global-foo", host.jsValueToString(value))
        assertEquals(1, scripts.size)
        assertTrue("eval must read globalThis[foo]", scripts[0].contains("globalThis["))
        assertTrue(scripts[0].contains("foo"))
    }

    @Test
    fun `js value clone and release`() {
        val context = host.jsContextCreate()
        host.jsEvalOverride = { "7" }

        val value = host.jsContextEval(context, "7")
        val clone = host.jsValueClone(value)
        assertTrue("clone is a fresh handle", clone != value)
        assertEquals("7", host.jsValueToString(clone))
        // original still alive after cloning
        assertEquals("7", host.jsValueToString(value))

        host.jsValueRelease(value)
        assertNull("released handle reports no value", host.jsValueToString(value))
        assertEquals("7", host.jsValueToString(clone))
    }

    // ---- webviews ----------------------------------------------------------

    @Test
    fun `js webview create load wait and eval`() {
        val webview = host.jsWebviewCreate()
        assertTrue("webview handle must be positive", webview > 0)

        // no load armed -> wait_for_load returns immediately
        host.jsWebviewWaitForLoad(webview)

        host.jsWebviewLoadUrl(webview, "https://example.com", emptyMap())
        val state = host.webviews[webview]
        assertNotNull(state)
        val view = state!!.webView
        // the load started on the shadow WebView
        assertEquals("https://example.com", shadowOf(view).lastLoadedUrl)

        // drive onPageFinished from a worker thread while the host waits
        val finisher = Thread {
            shadowOf(view).webViewClient?.onPageFinished(view, "https://example.com")
        }
        finisher.start()
        host.jsWebviewWaitForLoad(webview) // unblocks when the page finishes
        finisher.join()

        host.jsEvalOverride = { "\"page-title\"" }
        val title = host.jsWebviewEval(webview, "document.title")
        assertEquals("page-title", host.jsValueToString(title))
    }

    @Test
    fun `js webview html loads with a base url`() {
        val webview = host.jsWebviewCreate()
        host.jsWebviewLoadHtml(webview, "<html><body>hi</body></html>", "https://example.com/base/")
        // wait: no onPageFinished yet -> wait_for_load must not hang
        host.jsWebviewWaitForLoad(webview)
        val loaded = shadowOf(host.webviews[webview]!!.webView).lastLoadDataWithBaseURL
        assertNotNull("loadDataWithBaseURL was called", loaded)
        assertEquals("https://example.com/base/", loaded!!.baseUrl)
        assertEquals("<html><body>hi</body></html>", loaded.data)
    }

    @Test
    fun `js webview rule list gates user script injection`() {
        val webview = host.jsWebviewCreate()
        host.jsWebviewSetRuleList(webview, "^https://allowed\\.example\\.com")
        host.jsWebviewAddUserScript(webview, "window.__komorei = 1", atDocumentEnd = true, forMainFrameOnly = true)

        val client = shadowOf(host.webviews[webview]!!.webView).webViewClient
        assertNotNull("host wires a WebViewClient", client)

        val view = host.webviews[webview]!!.webView
        // non-matching URL -> nothing injected
        client!!.onPageFinished(view, "https://blocked.example.org/x")
        // matching URL -> script injected on page end
        client.onPageFinished(view, "https://allowed.example.com/x")
        assertEquals(
            "window.__komorei = 1",
            shadowOf(view).lastEvaluatedJavascript?.removePrefix("javascript:"),
        )
    }

    @Test
    fun `js webview cookies are read`() {
        val webview = host.jsWebviewCreate()
        val view = host.webviews[webview]!!.webView

        shadowOf(view).webViewClient?.onPageFinished(view, "https://example.com")
        CookieManager.getInstance().setCookie("https://example.com", "session=abc; path=/")

        val cookies = host.jsWebviewGetCookies(webview)
        assertEquals("abc", cookies["session"])
        assertEquals("https://example.com", host.webviews[webview]!!.lastUrl)
    }

    @Test
    fun `js webview delete cookie writes the expired cookie at the normalized url`() {
        val webview = host.jsWebviewCreate()
        val view = host.webviews[webview]!!.webView
        shadowOf(view).webViewClient?.onPageFinished(view, "https://example.com")

        host.jsWebviewDeleteCookie(webview, "session", "abc", ".example.com")

        // Robolectric's shadow CookieManager ignores expiry attributes, so the
        // cookie itself survives a delete here — but the host must still write
        // the expired-cookie directive to the URL normalized from the domain
        // (a wrong URL would leave this null).
        val written = CookieManager.getInstance().getCookie("https://example.com")
        assertNotNull("expired-cookie write landed on https://example.com", written)
    }
}