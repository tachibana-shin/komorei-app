package git.shin.komorei.ui.screens.advanced

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Actions behind the "Nâng cao" screen — the Android counterpart of Aidoku's
 * *Advanced* group (cache clears + log maintenance).
 *
 * The cookie wipe is deliberately the same one Settings uses: WebView cookies
 * feed every media request through `WebViewCookieJar`, so clearing them signs
 * the user out of every source at once.
 */
@HiltViewModel
class AdvancedViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val imageLoader: ImageLoader,
) : ViewModel() {

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** One-shot string-resource ids to surface as a Toast. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    /**
     * Drops every source's cached home layout so the next browse re-fetches.
     * (Aidoku's "Clear network cache" — Komorei has no OkHttp disk cache to
     * clear, so the equivalent win is the stored home/list data.)
     */
    fun clearSourceCache() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    @OptIn(ExperimentalCoilApi::class)
                    imageLoader.memoryCache?.clear()
                    // `sourcesFlow` is a Flow (registry-backed), so take one snapshot.
                    val sources = repository.sourcesFlow.first()
                    sources
                        .filter { !it.isAggregator }
                        .forEach { repository.clearCachedHome(it.id) }
                }
            }
            _messages.send(R.string.advanced_source_cache_cleared)
        }
    }

    fun clearCookies() {
        viewModelScope.launch {
            runCatching {
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
            }
            _messages.send(R.string.settings_cookies_cleared)
        }
    }
}
