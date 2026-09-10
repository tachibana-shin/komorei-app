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

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
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
