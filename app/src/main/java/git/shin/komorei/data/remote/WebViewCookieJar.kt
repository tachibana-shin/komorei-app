package git.shin.komorei.data.remote

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * A CookieJar that delegates to the system's [CookieManager] to share cookies
 * between WebView and OkHttpClient.
 */
class WebViewCookieJar : CookieJar {
    private val cookieManager = CookieManager.getInstance()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookiesString = cookieManager.getCookie(url.toString())
        if (cookiesString.isNullOrEmpty()) return emptyList()

        return cookiesString.split(";").mapNotNull {
            Cookie.parse(url, it.trim())
        }
    }
}

/**
 * Clears every stored cookie for [host] (e.g. `example.com` or `localhost:8080`):
 * reads the current cookie header for the host, then expires each cookie.
 * Android's [CookieManager] has no per-domain remove API, so this mirrors
 * [git.shin.komorei.sdk.KrxHostImpl]'s delete-cookie approach — write an
 * already-expired cookie per name (see the host's `jsWebviewDeleteCookie`).
 * Runs on both https and http URL forms so cookies set either way are dropped;
 * then flushes so the removal is persisted immediately.
 */
fun clearCookiesForHost(host: String) {
    val manager = CookieManager.getInstance()
    for (url in listOf("https://$host", "http://$host")) {
        val header = manager.getCookie(url) ?: continue
        val names =
            header.split(";").mapNotNull {
                it.trim().substringBefore("=").takeIf { name -> name.isNotEmpty() }
            }
        for (name in names) {
            manager.setCookie(
                url,
                "$name=; expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; path=/",
            )
        }
    }
    manager.flush()
}
