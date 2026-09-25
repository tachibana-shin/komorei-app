package git.shin.komorei.sdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import git.shin.komorei.data.LogLevel
import git.shin.komorei.data.LogStore
import git.shin.komorei.data.local.KrxDefaultsStore
import git.shin.komorei.data.remote.WEBVIEW_ANTI_FINGERPRINT_HEADERS
import git.shin.komorei.data.remote.stripFingerprintHeaders
import git.shin.komorei.data.local.krxDefaultsKey
import git.shin.komorei.data.local.platformKrxDefaultsStore
import git.shin.komorei.sdk.runner.HostDefaultValue
import git.shin.komorei.sdk.runner.HostHttpMethod
import git.shin.komorei.sdk.runner.HostNetResponse
import git.shin.komorei.sdk.runner.KomoreiHost
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking

/**
 * The real Kotlin implementation of the SDK host trait — every wasm import is
 * backed by the app's actual tooling (no stubs):
 *
 *  - `log_*`        → android Log
 *  - `sleep`/dates  → Thread / TimeZone / SimpleDateFormat
 *  - `defaults_*`   → SQLite (via [KrxDefaultsStore]); keys are namespaced by
 *    the host's source id (`{sourceId}.{key}`, Aidoku-style) when the host is
 *    scoped — see [scopedTo]
 *  - `net_request`  → blocking OkHttp (the app's shared client, so the
 *    WebViewCookieJar + Komorei User-Agent apply to every source request)
 *  - `html_*`       → Jsoup, over opaque i64 handles owned by this host
 *  - `js_*`         → real Android WebViews (JavaScript enabled): one hidden
 *    WebView per JS context, full WebViews for the `webview_*` family.
 *    Eval runs on the main thread via an async `evaluateJavascript` callback
 *    (awaited from the runner thread); page loads are tracked with a latch
 *    that `wait_for_load` blocks on.
 *
 * The wasm side receives handles (positive i64) instead of the Jsoup objects.
 * The runner stores a descriptor per handle and relays every `html_*` import
 * here. Handles stay valid until the wasm drops the object
 * (`std::destroy(rid)` → [htmlDestroy] / [jsValueRelease]).
 *
 * Jsoup objects are not thread-safe: all `html_*` calls for one runner are
 * serialized through the runner's engine mutex. [htmlDestroy] may be invoked
 * from media threads (segment interceptors), so the registry itself is
 * concurrent.
 *
 * `js_*` calls arrive on the runner thread (never the main thread) — each one
 * hops to the main looper and blocks until the WebView answers. When the host
 * is driven directly on the main thread (Robolectric tests) the hop is skipped
 * and [jsEvalOverride] supplies canned eval results (the paused test looper
 * can never deliver an async `evaluateJavascript` callback).
 *
 * Handle semantics follow the SDK contract (crates/runner/src/host.rs) and the
 * wasmer reference host:
 *  - `html_parse`/`html_parse_fragment` return a handle > 0 (<= 0 = InvalidHtml)
 *  - `html_kind`: 0 Unknown, 1 Node, 2 TextNode, 3 DataNode, 4 Comment,
 *    5 Element, 6 ElementList, 7 Document
 *  - string getters return null where the reference reports NoResult
 *    (missing attribute, no parent/sibling, ...)
 *  - mutators return false on a bad receiver (the runner maps it to
 *    InvalidDescriptor); `html_select` returns null on a bad selector
 *    (InvalidQuery)
 *  - `js_*` value/context/webview handles: 0/negative = failure
 */
class KrxHostImpl(
    context: Context,
    okHttpClient: OkHttpClient? = null,
    defaultsStore: KrxDefaultsStore = platformKrxDefaultsStore(context),
    /**
     * Source id that scopes this host's defaults keys — `{defaultNamespace}.{key}`,
     * Aidoku-style. `null` reads/writes raw keys (test hosts). Each `KrxSourceRegistry`
     * runner is bound to [scopedTo] twin so the wasm `defaults_get`/`defaults_set`
     * imports (which carry only the raw key) land in the owning source's rows.
     */
    defaultNamespace: String? = null,
) : KomoreiHost {

    private val appContext: Context = context.applicationContext
    private val client: OkHttpClient = okHttpClient ?: OkHttpClient.Builder().build()
    private val defaultNamespace: String? = defaultNamespace

    /**
     * The Krx defaults store (settings values behind `defaults_get`/`defaults_set`).
     * Exposed `internal` so tests can reach the exact store a host instance reads.
     */
    internal val defaultsStore: KrxDefaultsStore = defaultsStore

    /**
     * A twin of this host scoped to [sourceId] — shares the app context, OkHttp
     * client and defaults store, but namespaces every defaults key with
     * `{sourceId}.` (Aidoku-style). [KrxSourceRegistry] binds each runner to its
     * own scoped twin so `defaults_get("x")` in every source reads only that
     * source's rows.
     */
    internal fun scopedTo(sourceId: String): KrxHostImpl =
        KrxHostImpl(appContext, client, defaultsStore, sourceId)

    /** Opaque Jsoup object registry: handle → Document / Element / Node / Elements / node list. */
    private val nodes = ConcurrentHashMap<Long, Any>()
    private val nextHandle = AtomicLong(1)

    /** Opaque JS value registry (values produced by `js_*_eval`). */
    private val values = ConcurrentHashMap<Long, JsValueData>()

    /** JS contexts (hidden JS-enabled WebViews). Exposed `internal` for tests. */
    internal val contexts = ConcurrentHashMap<Long, JsWebViewState>()

    /** JS webviews (page-loading WebViews). Exposed `internal` for tests. */
    internal val webviews = ConcurrentHashMap<Long, JsWebViewState>()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Test seam: replaces the real `evaluateJavascript` result. Robolectric's
     * paused main looper never delivers the async callback, so host-level JS
     * tests inject canned raw results ("4", `"\"hi\""`, "null", ...) that
     * flow through the same [interpret] pipeline as production values.
     */
    internal var jsEvalOverride: ((script: String) -> String?)? = null

    /** A list of arbitrary nodes (from `childNodes` / node siblings), distinct from [Elements]. */
    private class NodeList(val children: List<Node>)

    private fun store(value: Any): Long {
        val handle = nextHandle.getAndIncrement()
        nodes[handle] = value
        return handle
    }

    // ---- env --------------------------------------------------------------

    override fun logPrint(message: String) {
        // A source's own println!/env::print — the "server log" shown on the
        // Logs screen. Scoped like every other host call so lines from several
        // sources stay distinguishable.
        LogStore.add(LogLevel.DEFAULT, message, defaultNamespace)
        Log.d(TAG, message)
    }

    override fun logAbort() {
        LogStore.error("source aborted (panic)", defaultNamespace)
        Log.e(TAG, "source aborted (panic)")
    }

    override fun sleep(seconds: Int) {
        try {
            Thread.sleep(seconds.toLong() * 1000L)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // ---- std dates --------------------------------------------------------

    override fun currentDate(): Double = System.currentTimeMillis() / 1000.0

    override fun utcOffset(): Long = TimeZone.getDefault().rawOffset.toLong() / 1000L

    override fun parseDate(date: String, format: String, locale: String?, timezone: String?): Double {
        return try {
            val loc = locale?.let { Locale.forLanguageTag(it.replace('_', '-')) } ?: Locale.getDefault()
            val fmt = SimpleDateFormat(format, loc)
            if (timezone != null) fmt.timeZone = TimeZone.getTimeZone(timezone)
            val parsed = fmt.parse(date) ?: return -1.0
            parsed.time / 1000.0
        } catch (e: Exception) {
            -1.0
        }
    }

    // ---- defaults (SQLite via KrxDefaultsStore) --------------------------
    //
    // Keys are namespaced by the host's source id (`{sourceId}.{key}`) when the
    // host is scoped — Aidoku prefixes every UserDefaults key the same way. The
    // wasm imports only carry the raw key; the scoping lives here on the host.

    override fun defaultsGet(key: String): HostDefaultValue? {
        return runBlocking { defaultsStore.get(scopedKey(key)) }
    }

    override fun defaultsSet(key: String, value: HostDefaultValue) {
        runBlocking { defaultsStore.set(scopedKey(key), value) }
    }

    private fun scopedKey(key: String): String =
        defaultNamespace?.let { krxDefaultsKey(it, key) } ?: key

    // ---- net (blocking OkHttp) --------------------------------------------

    override fun netRequest(
        method: HostHttpMethod,
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
        timeout: Double?,
    ): HostNetResponse {
        var result = tryExecuteRequest(
            url = url,
            method = method,
            headers = headers,
            body = body,
            timeout = timeout
        )

        // A 403..503 with challenge markers is a JS challenge (Cloudflare and
        // friends): clear it with a hidden WebView so JavaScript can set its
        // cookies, fall back to the visible browser dialog when a human is
        // needed, then retry with the fresh cookies (WebViewCookieJar rides
        // the shared CookieManager automatically).
        var retries = 0
        while (isChallengeResponse(result) && retries < MAX_403_RETRIES) {
            val solvedHeadless = runBlocking {
                JsChallengeCoordinator.withHeadlessLock { solveHeadlessOnce(url) }
            }
            val solved = if (solvedHeadless) {
                true
            } else {
                Log.w(TAG, "headless challenge not cleared for $url — asking the user")
                runBlocking { JsChallengeCoordinator.requestUserBypass(url) }
            }
            if (!solved) break
            retries++
            result = tryExecuteRequest(
                url = url,
                method = method,
                headers = headers,
                body = body,
                timeout = timeout
            )
        }

        return result
    }

    /**
     * True when [result] looks like a JS challenge — a page that runs a script
     * and sets a cookie when a human/browser "proves" itself — rather than a
     * plain error. A bare 403 (auth, geo-block, hotlink policy) is returned
     * as-is: no WebView, no user dialog, no retry.
     */
    private fun isChallengeResponse(result: HostNetResponse): Boolean {
        if (!result.ok) return false
        if (result.status !in 403..503) return false
        val contentType = result.headers.entries.firstOrNull { (name, _) ->
            name.equals("Content-Type", ignoreCase = true)
        }?.value.orEmpty().lowercase()
        if (!contentType.contains("text/html")) return false
        val body = String(result.data, Charsets.UTF_8)
        return CHALLENGE_MARKERS.any { body.contains(it, ignoreCase = true) } ||
            body.contains("<title></title>", ignoreCase = true) // empty-title JS redirect
    }

    /** Executes an HTTP request via OkHttp, returning the raw [HostNetResponse]. */
    private fun tryExecuteRequest(
        url: String,
        method: HostHttpMethod,
        headers: Map<String, String>,
        body: ByteArray,
        timeout: Double?,
    ): HostNetResponse {
        return try {
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            requestBuilder.withMethod(method, body)
            val effectiveClient = if (timeout != null && timeout > 0) {
                client.newBuilder()
                    .callTimeout((timeout * 1000).toLong(), TimeUnit.MILLISECONDS)
                    .build()
            } else {
                client
            }
            effectiveClient.newCall(requestBuilder.build()).execute().use { response ->
                val bodyBytes = response.body.let { it.bytes() } ?: byteArrayOf()
                val responseHeaders = LinkedHashMap<String, String>()
                response.headers.forEach { (name, value) ->
                    responseHeaders.merge(name, value) { existing, new -> "$existing, $new" }
                }
                HostNetResponse(
                    ok = true,
                    status = response.code,
                    url = response.request.url.toString(),
                    headers = responseHeaders,
                    data = bodyBytes,
                )
            }
        } catch (e: Exception) {
            // Mirrors Aidoku's requestHandler, which logs the failed request
            // against the source id before rethrowing.
            LogStore.error("net_request failed: ${method.name} $url -> $e", defaultNamespace)
            Log.w(TAG, "net_request failed: ${method.name} $url -> $e")
            HostNetResponse(ok = false, status = 0, url = url, headers = emptyMap(), data = byteArrayOf())
        }
    }

    /**
     * Loads [url] in a hidden JS-enabled WebView, waits for the page, then
     * polls the DOM until the challenge markers are gone (or a timeout).
     * Returns true when the challenge cleared.
     *
     * The solver cookies land in the shared [android.webkit.CookieManager], so
     * the follow-up OkHttp retry rides the same session. The load + poll run on
     * the runner thread — the main thread stays free to deliver `onPageFinished`
     * and `evaluateJavascript` callbacks (blocking it here previously deadlocked:
     * every 403 froze the app for the full load timeout and never solved anything).
     */
    private fun solveHeadlessOnce(url: String): Boolean {
        val state = onMainThread { createWebViewState() }
        try {
            postToMain {
                state.webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                CookieManager.getInstance().setAcceptCookie(true)
                state.beginLoad()
                state.webView.loadUrl(url, WEBVIEW_ANTI_FINGERPRINT_HEADERS)
            }
            onChallengeWebViewCreated?.invoke(state.webView)
            // On the runner thread, wait for the page; on the main thread
            // (Robolectric) the shadow WebView never delivers callbacks on its
            // own, so the poll below drives the flow via jsEvalOverride.
            if (Looper.myLooper() !== Looper.getMainLooper()) {
                state.awaitLoaded(challengeLoadTimeoutMs)
            }
            return awaitChallengeClear(state.webView)
        } catch (e: Exception) {
            Log.w(TAG, "headless JS challenge failed for $url: $e")
            return false
        } finally {
            postToMain { state.webView.destroy() }
        }
    }

    /**
     * Polls the page until the challenge markers disappear. Returns true on
     * success or false when the window elapses without a clear.
     */
    private fun awaitChallengeClear(view: WebView): Boolean {
        // Real monotonic time, NOT SystemClock.uptimeMillis(): the latter is a
        // frozen shadow clock under Robolectric (PAUSED looper), so the deadline
        // would never elapse and this loop would spin forever in tests.
        val deadline = System.nanoTime() + challengeSolveTimeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            val stillChallenging = evalBool(view, CHALLENGE_CHECK_JS)
            if (stillChallenging == false) return true
            // null = eval unavailable (no result yet); not proof of a clear.
            Thread.sleep(challengePollIntervalMs)
        }
        return false
    }

    /**
     * Evaluates [script] and interprets the WebView callback value as a JSON
     * boolean (`true` / `"true"` / `false` / `"false"`), or null when no value
     * came back at all.
     */
    private fun evalBool(view: WebView, script: String): Boolean? {
        val raw = evaluate(view, script)?.trim()
        return when (raw) {
            "true", "\"true\"" -> true
            "false", "\"false\"" -> false
            else -> null
        }
    }

    /**
     * Runs [block] synchronously when already on the main thread (Robolectric
     * tests drive the shadow WebView directly), otherwise posts it and returns
     * immediately so the main looper stays free for WebView callbacks.
     */
    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() === Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    // ---- html DOM (Jsoup) -------------------------------------------------

    override fun htmlParse(html: String, baseUrl: String): Long = try {
        store(Jsoup.parse(html, baseUrl))
    } catch (e: Exception) {
        0
    }

    override fun htmlParseFragment(html: String, baseUrl: String): Long = try {
        store(Jsoup.parseBodyFragment(html, baseUrl))
    } catch (e: Exception) {
        0
    }

    override fun htmlEscape(text: String): String? = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
        }
    }

    override fun htmlUnescape(text: String): String? = try {
        Entities.unescape(text)
    } catch (e: Exception) {
        null
    }

    override fun htmlKind(handle: Long): Int = when (val node = nodes[handle]) {
        is TextNode -> KIND_TEXT_NODE
        is DataNode -> KIND_DATA_NODE
        is Comment -> KIND_COMMENT
        is Document -> KIND_DOCUMENT
        is Element -> KIND_ELEMENT
        is Elements -> KIND_ELEMENT_LIST
        is Node -> KIND_NODE
        else -> KIND_UNKNOWN
    }

    override fun htmlAttr(handle: Long, key: String): String? {
        return when (val node = nodes[handle]) {
            is Element -> if (node.hasAttr(key)) node.attr(key) else null
            is Elements -> node.firstOrNull()?.takeIf { it.hasAttr(key) }?.attr(key)
            else -> null
        }
    }

    override fun htmlHasAttr(handle: Long, key: String): Boolean {
        return when (val node = nodes[handle]) {
            is Element -> node.hasAttr(key)
            is Elements -> node.firstOrNull()?.hasAttr(key) == true
            else -> false
        }
    }

    override fun htmlSetAttr(handle: Long, key: String, value: String): Boolean = try {
        when (val node = nodes[handle]) {
            is Element -> {
                node.attr(key, value)
                true
            }
            is Elements -> {
                node.forEach { it.attr(key, value) }
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlRemoveAttr(handle: Long, key: String): Boolean = try {
        when (val node = nodes[handle]) {
            is Element -> {
                node.removeAttr(key)
                true
            }
            is Elements -> {
                node.forEach { it.removeAttr(key) }
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlSelect(handle: Long, query: String): Long? = try {
        val elements = when (val node = nodes[handle]) {
            is Document -> node.select(query)
            is Element -> node.select(query)
            is Elements -> node.select(query)
            else -> return null
        }
        store(elements)
    } catch (e: Exception) {
        null
    }

    override fun htmlSelectFirst(handle: Long, query: String): Long? = try {
        val element = when (val node = nodes[handle]) {
            is Document -> node.selectFirst(query)
            is Element -> node.selectFirst(query)
            is Elements -> node.select(query).firstOrNull()
            else -> return null
        } ?: return null
        store(element)
    } catch (e: Exception) {
        null
    }

    override fun htmlText(handle: Long): String? {
        return when (val node = nodes[handle]) {
            is TextNode -> node.getWholeText()
            is Document -> node.text()
            is Element -> node.text()
            is Elements -> node.text()
            else -> null
        }
    }

    override fun htmlOwnText(handle: Long): String? {
        return when (val node = nodes[handle]) {
            is TextNode -> node.getWholeText()
            is Document -> node.ownText()
            is Element -> node.ownText()
            else -> null
        }
    }

    override fun htmlUntrimmedText(handle: Long): String? {
        return when (val node = nodes[handle]) {
            is Document -> node.wholeText()
            is Element -> node.wholeText()
            is Elements -> node.joinToString("") { it.wholeText() }
            else -> null
        }
    }

    override fun htmlHtml(handle: Long): String? {
        return when (val node = nodes[handle]) {
            is Element -> node.html()
            is Elements -> node.firstOrNull()?.html()
            else -> null
        }
    }

    override fun htmlOuterHtml(handle: Long): String? {
        return when (val node = nodes[handle]) {
            is Element -> node.outerHtml()
            is Elements -> node.firstOrNull()?.outerHtml()
            else -> null
        }
    }

    override fun htmlId(handle: Long): String? {
        return (nodes[handle] as? Element)?.id()?.takeIf { it.isNotBlank() }
    }

    override fun htmlTagName(handle: Long): String? {
        return (nodes[handle] as? Element)?.tagName()
    }

    override fun htmlClassName(handle: Long): String? {
        return (nodes[handle] as? Element)?.className()?.takeIf { it.isNotBlank() }
    }

    override fun htmlHasClass(handle: Long, `class`: String): Boolean {
        return when (val node = nodes[handle]) {
            is Element -> node.hasClass(`class`)
            is Elements -> node.firstOrNull()?.hasClass(`class`) == true
            else -> false
        }
    }

    override fun htmlAddClass(handle: Long, `class`: String): Boolean = try {
        when (val node = nodes[handle]) {
            is Element -> {
                node.addClass(`class`)
                true
            }
            is Elements -> {
                node.forEach { it.addClass(`class`) }
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlRemoveClass(handle: Long, `class`: String): Boolean = try {
        when (val node = nodes[handle]) {
            is Element -> {
                node.removeClass(`class`)
                true
            }
            is Elements -> {
                node.forEach { it.removeClass(`class`) }
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlSize(handle: Long): Int {
        return when (val node = nodes[handle]) {
            is Elements -> node.size
            is NodeList -> node.children.size
            else -> -1
        }
    }

    override fun htmlFirst(handle: Long): Long? {
        return when (val node = nodes[handle]) {
            is Elements -> node.firstOrNull()?.let(::store)
            is NodeList -> node.children.firstOrNull()?.let(::store)
            else -> null
        }
    }

    override fun htmlLast(handle: Long): Long? {
        return when (val node = nodes[handle]) {
            is Elements -> node.lastOrNull()?.let(::store)
            is NodeList -> node.children.lastOrNull()?.let(::store)
            else -> null
        }
    }

    override fun htmlGet(handle: Long, index: Long): Long? {
        if (index < 0 || index > Int.MAX_VALUE) return null
        return when (val node = nodes[handle]) {
            is Elements -> node.getOrNull(index.toInt())?.let(::store)
            is NodeList -> node.children.getOrNull(index.toInt())?.let(::store)
            else -> null
        }
    }

    override fun htmlParent(handle: Long): Long? {
        return when (val node = nodes[handle]) {
            is Element -> node.parent()?.let(::store)
            is Node -> node.parent()?.let(::store)
            else -> null
        }
    }

    override fun htmlNext(handle: Long): Long? {
        return when (val node = nodes[handle]) {
            is Element -> node.nextElementSibling()?.let(::store)
            is Node -> node.nextSibling()?.let(::store)
            else -> null
        }
    }

    override fun htmlPrevious(handle: Long): Long? {
        return when (val node = nodes[handle]) {
            is Element -> node.previousElementSibling()?.let(::store)
            is Node -> node.previousSibling()?.let(::store)
            else -> null
        }
    }

    override fun htmlSiblings(handle: Long): Long? {
        return when (val node = nodes[handle]) {
            is Element -> {
                val parent = node.parent() ?: return null
                // NB: never build this via `Elements(...).remove(node)` — jsoup's
                // Elements overrides `remove(int)` to call `element.remove()`,
                // which DETACHES the sibling from the source tree (silently
                // corrupting every later node/sibling/next query).
                val siblings = Elements(parent.children().filterNot { it === node })
                store(siblings)
            }
            is Node -> {
                val parent = node.parent() ?: return null
                val siblings = parent.childNodes().toMutableList()
                siblings.remove(node)
                store(NodeList(siblings))
            }
            else -> null
        }
    }

    override fun htmlChildren(handle: Long): Long? {
        return when (val node = nodes[handle]) {
            is Element -> store(node.children())
            else -> null
        }
    }

    override fun htmlChildNodes(handle: Long): Long? {
        return when (val node = nodes[handle]) {
            is Node -> store(NodeList(node.childNodes()))
            else -> null
        }
    }

    override fun htmlBaseUri(handle: Long): String? {
        return (nodes[handle] as? Element)?.baseUri()?.takeIf { it.isNotBlank() }
    }

    override fun htmlData(handle: Long): String? {
        return when (val node = nodes[handle]) {
            is DataNode -> node.wholeData
            is Comment -> node.data
            is TextNode -> node.getWholeText()
            // SwiftSoup semantics: an Element's "data" is the accumulation of
            // its descendant DataNode/Comment contents — e.g. the raw JSON
            // inside a `<script id="srcData">` tag. (`attr("data")` would be
            // the HTML attribute, which scripts used for embedded JSON never
            // carry.)
            is Element -> buildString {
                accumulateData(node, this)
            }.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun accumulateData(node: org.jsoup.nodes.Node, out: StringBuilder) {
        for (child in node.childNodes()) {
            when (child) {
                is DataNode -> out.append(child.wholeData)
                is Comment -> out.append(child.data)
                else -> accumulateData(child, out)
            }
        }
    }

    override fun htmlSetText(handle: Long, text: String): Boolean = try {
        when (val node = nodes[handle]) {
            is Element -> {
                node.text(text)
                true
            }
            is TextNode -> {
                node.text(text)
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlSetHtml(handle: Long, html: String): Boolean = try {
        when (val node = nodes[handle]) {
            is Element -> {
                node.html(html)
                true
            }
            is Elements -> {
                node.forEach { it.html(html) }
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlPrepend(handle: Long, html: String): Boolean = try {
        when (val node = nodes[handle]) {
            is Element -> {
                node.prepend(html)
                true
            }
            is Elements -> {
                node.forEach { it.prepend(html) }
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlAppend(handle: Long, html: String): Boolean = try {
        when (val node = nodes[handle]) {
            is Element -> {
                node.append(html)
                true
            }
            is Elements -> {
                node.forEach { it.append(html) }
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlRemove(handle: Long): Boolean = try {
        when (val node = nodes[handle]) {
            is Node -> {
                node.remove()
                true
            }
            else -> false
        }
    } catch (e: Exception) {
        false
    }

    override fun htmlDestroy(handle: Long) {
        nodes.remove(handle)
    }

    // ---- js (WebView) -----------------------------------------------------

    /**
     * Test seam: the headless JS-challenge WebView created by [solveHeadlessOnce].
     * Robolectric's shadow WebView cannot deliver page callbacks on its own, so
     * tests grab the view here and drive them.
     */
    internal var onChallengeWebViewCreated: ((WebView) -> Unit)? = null

    /** Test seam: headless challenge poll interval (production = 1.5s). */
    internal var challengePollIntervalMs: Long = CHALLENGE_POLL_INTERVAL_MS

    /** Test seam: headless challenge solve window (production = 15s). */
    internal var challengeSolveTimeoutMs: Long = CHALLENGE_SOLVE_TIMEOUT_MS

    /** Test seam: headless challenge page-load wait (production = 15s). */
    internal var challengeLoadTimeoutMs: Long = CHALLENGE_LOAD_TIMEOUT_MS

    /** A JS value produced by `evaluateJavascript` (WebView returns JSON). */
    private sealed class JsValueData {
        object Undefined : JsValueData()
        object Null : JsValueData()
        data class Bool(val value: Boolean) : JsValueData()
        data class Number(val value: Double) : JsValueData()
        data class Str(val value: String) : JsValueData()
    }

    /** User script registered via `webview.addUserScript`. */
    internal class UserScript(val code: String, val atDocumentEnd: Boolean, val forMainFrameOnly: Boolean)

    /**
     * A JS-capable WebView (context or webview) plus the load tracking the
     * wasm drives through `wait_for_load`.
     */
    internal class JsWebViewState(val webView: WebView) {
        @Volatile
        var lastUrl: String = ""

        private val loadLock = Any()
        private var loadLatch: CountDownLatch? = null

        val userScripts = ConcurrentLinkedQueue<UserScript>()
        val rulePatterns = ConcurrentLinkedQueue<Regex>()

        /** Arm the load latch BEFORE starting a navigation. */
        fun beginLoad() {
            synchronized(loadLock) { loadLatch = CountDownLatch(1) }
        }

        /** Release waiters when `onPageFinished` fires (or the load fails). */
        fun finishLoad(url: String?) {
            if (url != null) lastUrl = url
            synchronized(loadLock) {
                loadLatch?.countDown()
                loadLatch = null
            }
        }

        /** Block the caller until the armed load finishes (skip if none). */
        fun awaitLoaded(timeoutMillis: Long) {
            val latch = synchronized(loadLock) { loadLatch } ?: return
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
        }

        /** Empty rule list = inject user scripts everywhere. */
        fun rulesAllow(url: String): Boolean =
            rulePatterns.isEmpty() || rulePatterns.any { it.containsMatchIn(url) }
    }

    private fun createWebViewState(): JsWebViewState {
        val view = WebView(appContext)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.javaScriptCanOpenWindowsAutomatically = false
        view.settings.allowFileAccess = false
        val state = JsWebViewState(view)
        view.webViewClient = object : WebViewClient() {
            @Suppress("DEPRECATION") // favicon variant: the only onPageStarted override
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (url != null) injectUserScripts(state, view, url, atDocumentEnd = false)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                state.finishLoad(url)
                if (url != null) injectUserScripts(state, view, url, atDocumentEnd = true)
            }

            // Anti-bot systems fingerprint Android's autogenerated
            // `X-Requested-With` header to detect WebViews (the reference app
            // strips it the same way); keep the load pipeline clean of it too.
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? =
                super.shouldInterceptRequest(view, stripFingerprintHeaders(request))
        }
        return state
    }

    /**
     * Inject registered user scripts on page start/end. `onPageStarted` is a
     * main-frame event; subframe-only scripts are not yet distinguished (the
     * [UserScript.forMainFrameOnly] flag is kept but WebView does not expose
     * the frame here).
     */
    private fun injectUserScripts(state: JsWebViewState, view: WebView, url: String, atDocumentEnd: Boolean) {
        if (!state.rulesAllow(url)) return
        state.userScripts.forEach { script ->
            if (script.atDocumentEnd == atDocumentEnd) {
                try {
                    view.evaluateJavascript(script.code, null)
                } catch (e: Exception) {
                    Log.w(TAG, "user script injection failed at ${if (atDocumentEnd) "end" else "start"}: $e")
                }
            }
        }
    }

    /** Run [block] on the main thread and block the caller until it returns. */
    private fun <T> onMainThread(timeoutMillis: Long = MAIN_HOP_TIMEOUT_MS, block: () -> T): T {
        if (Looper.myLooper() === Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        mainHandler.post {
            result = runCatching(block)
            latch.countDown()
        }
        check(latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) { "main-thread hop timed out" }
        return result!!.getOrThrow()
    }

    /**
     * Evaluate JS and return the raw WebView result (JSON string).
     *
     * Real eval is inherently async: the callback is posted back to the main
     * looper, so callers on a runner thread wait here. When the host is driven
     * from the main thread (Robolectric), the paused looper can never deliver
     * the callback — [jsEvalOverride] supplies the value instead, and without
     * one the eval is fire-and-forget.
     */
    private fun evaluate(view: WebView, script: String): String? {
        jsEvalOverride?.let { return it(script) }
        if (Looper.myLooper() === Looper.getMainLooper()) {
            view.evaluateJavascript(script, null)
            return null
        }
        val output = AtomicReference<String?>()
        val done = CountDownLatch(1)
        mainHandler.post {
            try {
                view.evaluateJavascript(script) { value ->
                    output.set(value)
                    done.countDown()
                }
            } catch (e: Exception) {
                Log.w(TAG, "evaluateJavascript failed: $e")
                done.countDown()
            }
        }
        if (!done.await(EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            // The callback never landed — the result below is whatever the last
            // successful eval left behind, or null. Log it so a flaky WebView is
            // distinguishable from a source that genuinely returned nothing.
            Log.w(TAG, "evaluateJavascript timed out after ${EVAL_TIMEOUT_MS}ms")
        }
        return output.get()
    }

    /** Evaluate and register the result as a value handle (0 = failure). */
    private fun evalHandle(view: WebView, script: String): Long = try {
        storeValue(interpret(evaluate(view, script)))
    } catch (e: Exception) {
        Log.w(TAG, "js eval failed: $script -> $e")
        0
    }

    private fun storeValue(value: JsValueData): Long {
        val handle = nextHandle.getAndIncrement()
        values[handle] = value
        return handle
    }

    /** Interpret WebView's JSON result into a [JsValueData]. */
    private fun interpret(raw: String?): JsValueData {
        if (raw == null) return JsValueData.Undefined
        return when (val t = raw.trim()) {
            "null" -> JsValueData.Null
            "undefined" -> JsValueData.Undefined
            "true" -> JsValueData.Bool(true)
            "false" -> JsValueData.Bool(false)
            else -> when {
                t.startsWith("\"") -> JsValueData.Str(decodeJsString(t))
                t.toDoubleOrNull() != null -> JsValueData.Number(t.toDouble())
                else -> JsValueData.Str(t)
            }
        }
    }

    private fun decodeJsString(quoted: String): String = try {
        (JSONTokener(quoted).nextValue() as? String) ?: quoted
    } catch (e: Exception) {
        quoted
    }

    private fun JsValueData.toJsString(): String? = when (this) {
        is JsValueData.Undefined -> null
        is JsValueData.Null -> "null"
        is JsValueData.Bool -> value.toString()
        is JsValueData.Number -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is JsValueData.Str -> value
    }

    private fun JsValueData.truthy(): Boolean = when (this) {
        is JsValueData.Undefined, is JsValueData.Null -> false
        is JsValueData.Bool -> value
        is JsValueData.Number -> value != 0.0
        is JsValueData.Str -> value.isNotEmpty()
    }

    private fun JsValueData.toJsInt(): Int = when (this) {
        is JsValueData.Undefined, is JsValueData.Null -> 0
        is JsValueData.Bool -> if (value) 1 else 0
        is JsValueData.Number -> value.toInt()
        is JsValueData.Str -> value.toIntOrNull() ?: 0
    }

    private fun JsValueData.toJsF64(): Double = when (this) {
        is JsValueData.Undefined, is JsValueData.Null -> 0.0
        is JsValueData.Bool -> if (value) 1.0 else 0.0
        is JsValueData.Number -> value
        is JsValueData.Str -> value.toDoubleOrNull() ?: 0.0
    }

    override fun jsContextCreate(): Long = onMainThread {
        val handle = nextHandle.getAndIncrement()
        contexts[handle] = createWebViewState()
        handle
    }

    override fun jsContextEval(handle: Long, code: String): Long {
        val context = contexts[handle] ?: return 0
        return evalHandle(context.webView, code)
    }

    override fun jsContextGet(handle: Long, key: String): Long {
        val context = contexts[handle] ?: return 0
        return evalHandle(context.webView, "globalThis[${JSONObject.quote(key)}]")
    }

    override fun jsValueToString(handle: Long): String? = values[handle]?.toJsString()

    override fun jsValueToBool(handle: Long): Boolean = values[handle]?.truthy() ?: false

    override fun jsValueToInt(handle: Long): Int = values[handle]?.toJsInt() ?: 0

    override fun jsValueToF64(handle: Long): Double = values[handle]?.toJsF64() ?: 0.0

    override fun jsValueIsDefined(handle: Long): Boolean = values[handle] !is JsValueData.Undefined

    override fun jsValueIsNull(handle: Long): Boolean = values[handle] is JsValueData.Null

    override fun jsValueClone(handle: Long): Long {
        val value = values[handle] ?: return 0
        return storeValue(value)
    }

    override fun jsValueRelease(handle: Long) {
        values.remove(handle)
    }

    override fun jsWebviewCreate(): Long = onMainThread {
        val handle = nextHandle.getAndIncrement()
        webviews[handle] = createWebViewState()
        handle
    }

    override fun jsWebviewSetRuleList(handle: Long, rules: String) {
        val state = webviews[handle] ?: return
        state.rulePatterns.clear()
        rules.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                try {
                    state.rulePatterns.add(Regex(trimmed))
                } catch (e: Exception) {
                    Log.w(TAG, "skipping invalid rule regex: $trimmed -> $e")
                }
            }
        }
    }

    override fun jsWebviewLoadUrl(handle: Long, url: String, headers: Map<String, String>) {
        val state = webviews[handle] ?: return
        onMainThread {
            state.beginLoad()
            try {
                if (headers.isEmpty()) {
                    state.webView.loadUrl(url)
                } else {
                    state.webView.loadUrl(url, headers)
                }
            } catch (e: Exception) {
                Log.w(TAG, "webview load failed: $url -> $e")
                state.finishLoad(url)
            }
        }
    }

    override fun jsWebviewLoadHtml(handle: Long, html: String, baseUrl: String) {
        val state = webviews[handle] ?: return
        onMainThread {
            state.beginLoad()
            try {
                state.webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
            } catch (e: Exception) {
                Log.w(TAG, "webview loadHtml failed: $e")
                state.finishLoad(baseUrl)
            }
        }
    }

    override fun jsWebviewWaitForLoad(handle: Long) {
        val state = webviews[handle] ?: return
        state.awaitLoaded(WEBVIEW_LOAD_TIMEOUT_MS)
    }

    override fun jsWebviewEval(handle: Long, code: String): Long {
        val state = webviews[handle] ?: return 0
        return evalHandle(state.webView, code)
    }

    override fun jsWebviewAddUserScript(handle: Long, code: String, atDocumentEnd: Boolean, forMainFrameOnly: Boolean) {
        webviews[handle]?.userScripts?.add(UserScript(code, atDocumentEnd, forMainFrameOnly))
    }

    override fun jsWebviewGetCookies(handle: Long): Map<String, String> {
        val state = webviews[handle] ?: return emptyMap()
        return onMainThread {
            val header = CookieManager.getInstance().getCookie(state.lastUrl)
            header?.let(::parseCookieHeader) ?: emptyMap()
        }
    }

    override fun jsWebviewDeleteCookie(handle: Long, name: String, value: String, domain: String) {
        // Guard only: the value is irrelevant, we just refuse to touch a webview
        // the source has already released.
        if (webviews[handle] == null) return
        onMainThread {
            try {
                val cookieUrl = when {
                    domain.startsWith("http://") || domain.startsWith("https://") -> domain
                    domain.startsWith(".") -> "https://" + domain.removePrefix(".")
                    else -> "https://$domain"
                }
                CookieManager.getInstance().apply {
                    // Expire the cookie so WebView stops sending it.
                    setCookie(cookieUrl, "$name=$value; expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; path=/")
                    flush()
                }
            } catch (e: Exception) {
                Log.w(TAG, "delete cookie failed: $name@$domain -> $e")
            }
        }
    }

    private fun parseCookieHeader(header: String): Map<String, String> {
        val cookies = LinkedHashMap<String, String>()
        header.split(";").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.isNotEmpty()) {
                val (name, rawValue) = trimmed.split("=", limit = 2)
                    .let { it[0] to (it.getOrNull(1) ?: "") }
                cookies[name] = rawValue
            }
        }
        return cookies
    }

    private fun Request.Builder.withMethod(method: HostHttpMethod, body: ByteArray) {
        when (method) {
            HostHttpMethod.GET -> get()
            HostHttpMethod.HEAD -> head()
            HostHttpMethod.POST -> post(body.toRequestBody())
            HostHttpMethod.PUT -> put(body.toRequestBody())
            HostHttpMethod.PATCH -> patch(body.toRequestBody())
            HostHttpMethod.DELETE -> if (body.isEmpty()) delete() else delete(body.toRequestBody())
            HostHttpMethod.OPTIONS -> method("OPTIONS", null)
            HostHttpMethod.CONNECT -> method("CONNECT", null)
            HostHttpMethod.TRACE -> method("TRACE", null)
        }
    }

    private companion object {
        const val TAG = "KrxHost"
        const val MAX_403_RETRIES = 2

        /**
         * Headless solve timing. 15s matches the reference app's headless
         * timeout; the poll interval keeps the main-thread roundtrips light.
         */
        const val CHALLENGE_LOAD_TIMEOUT_MS = 15_000L
        const val CHALLENGE_SOLVE_TIMEOUT_MS = 15_000L
        const val CHALLENGE_POLL_INTERVAL_MS = 1_500L

        /** Body markers that identify an HTML 403..503 as a JS challenge. */
        val CHALLENGE_MARKERS = listOf(
            "cf-challenge",
            "cf-browser-verification",
            "cf-error-details",
            "challenge-platform",
            "Just a moment",
            "Xác Minh An Toàn",
            "Xác minh khu vực",
            "Lỗi Server",
            "captcha",
        )

        /**
         * Returns "true" while the challenge is still up, "false" once the
         * document no longer carries any challenge markers. Mirrors the
         * reference app's headless completion check.
         */
        const val CHALLENGE_CHECK_JS = "(function(){" +
            "var b=document.body;if(!b)return\"true\";" +
            "if(document.title&&document.title.indexOf(\"Just a moment\")!==-1)return\"true\";" +
            "var t=b.innerText||\"\";" +
            "if(t.indexOf(\"cf-challenge\")!==-1)return\"true\";" +
            "if(t.indexOf(\"cf-browser-verification\")!==-1)return\"true\";" +
            "if(t.indexOf(\"ray-id\")!==-1)return\"true\";" +
            "if(t.indexOf(\"Xác Minh An Toàn\")!==-1)return\"true\";" +
            "if(t.indexOf(\"Xác minh khu vực\")!==-1)return\"true\";" +
            "if(document.querySelector(\".captcha-placeholder\")!==null)return\"true\";" +
            "return\"false\";" +
            "})()"

        const val KIND_UNKNOWN = 0
        const val KIND_NODE = 1
        const val KIND_TEXT_NODE = 2
        const val KIND_DATA_NODE = 3
        const val KIND_COMMENT = 4
        const val KIND_ELEMENT = 5
        const val KIND_ELEMENT_LIST = 6
        const val KIND_DOCUMENT = 7

        /** Upper bound for hopping JS work onto the main thread. */
        const val MAIN_HOP_TIMEOUT_MS = 30_000L

        /** Upper bound for one `evaluateJavascript` round trip. */
        const val EVAL_TIMEOUT_MS = 30_000L

        /** Upper bound for `webview.wait_for_load`. */
        const val WEBVIEW_LOAD_TIMEOUT_MS = 60_000L
    }
}