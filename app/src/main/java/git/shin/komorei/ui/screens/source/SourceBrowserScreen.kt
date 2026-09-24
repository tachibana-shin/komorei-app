package git.shin.komorei.ui.screens.source

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import git.shin.komorei.R
import git.shin.komorei.data.remote.WEBVIEW_ANTI_FINGERPRINT_HEADERS
import git.shin.komorei.data.remote.stripFingerprintHeaders
import git.shin.komorei.ui.components.search.CompactInput
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus
import kotlinx.coroutines.launch

/**
 * A real in-app WebView browser. Its cookies land in the shared
 * `CookieManager` — the same store that backs [git.shin.komorei.data.network.WebViewCookieJar]
 * — so a source login here is instantly available to every media request: no
 * plumbing needed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceBrowserScreen(
    initialUrl: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf(initialUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun load() {
        val wv = webView ?: return
        if (urlText.isBlank()) return
        val target = if (urlText.startsWith("http://", true) || urlText.startsWith("https://", true)) urlText else "https://$urlText"
        wv.loadUrl(target, WEBVIEW_ANTI_FINGERPRINT_HEADERS)
    }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardDark)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Toolbar: back / forward / reload + URL bar + share/open ────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                enabled = canGoBack,
                onClick = { webView?.goBack() },
                modifier = Modifier
                    // TV focus highlight (no-op on phones).
                    .tvFocus(shape = CircleShape, scale = 1.15f),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.source_browser_cd_back),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                enabled = canGoForward,
                onClick = { webView?.goForward() },
                modifier = Modifier
                    // TV focus highlight (no-op on phones).
                    .tvFocus(shape = CircleShape, scale = 1.15f),
            ) {
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.source_browser_cd_forward),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { webView?.reload() },
                modifier = Modifier
                    // TV focus highlight (no-op on phones).
                    .tvFocus(shape = CircleShape, scale = 1.15f),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.source_browser_cd_reload),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            CompactInput(
                value = urlText,
                onValueChange = { urlText = it },
                hint = stringResource(R.string.source_browser_url_hint),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { load() }),
            )

            TextButton(
                onClick = { load() },
                modifier = Modifier
                    // TV focus highlight (no-op on phones).
                    .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
            ) {
                Text(
                    text = stringResource(R.string.source_browser_cd_go),
                    color = TextPrimary,
                )
            }
        }

        // ── Share / open-external row ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    val url = webView?.url ?: return@TextButton
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
                modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.source_browser_cd_share),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.source_browser_cd_share),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            TextButton(
                onClick = {
                    val url = webView?.url ?: return@TextButton
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
            ) {
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.source_browser_cd_open_external),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.source_browser_cd_open_external),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        // ── WebView ───────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = false

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            url?.let { urlText = it }
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            canGoBack = view.canGoBack()
                            canGoForward = view.canGoForward()
                        }

                        // Anti-bot systems fingerprint Android's autogenerated
                        // `X-Requested-With` header to detect WebViews; strip it
                        // like the challenge-bypass dialog (reference app).
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? =
                            super.shouldInterceptRequest(view, stripFingerprintHeaders(request))

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            // Swallow — an error page is not useful inside the
                            // app browser; the user can just reload.
                        }
                    }
                    webChromeClient = WebChromeClient()

                    webView = this
                    if (initialUrl.isNotBlank()) {
                        loadUrl(initialUrl, WEBVIEW_ANTI_FINGERPRINT_HEADERS)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(CardDark),
            update = { view ->
                webView = view
            },
        )
    }
}
