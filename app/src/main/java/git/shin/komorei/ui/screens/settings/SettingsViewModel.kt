package git.shin.komorei.ui.screens.settings

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.BuildConfig
import git.shin.komorei.R
import git.shin.komorei.data.SearchHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * App-wide settings actions. There is no persisted preference yet (the app is
 * dark-only and source options live in per-source settings) — this focuses on
 * cache/cookie/search-history maintenance plus the source-repo entry point,
 * which previously was only reachable from the Nguồn tab.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val imageLoader: ImageLoader,
    private val searchHistoryStore: SearchHistoryStore,
) : ViewModel() {

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** One-shot string-resource ids to surface as a Toast. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    val appVersion: String = BuildConfig.VERSION_NAME

    @OptIn(ExperimentalCoilApi::class)
    fun clearImageCache() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    imageLoader.memoryCache?.clear()
                    imageLoader.diskCache?.clear()
                }
            }
            _messages.send(R.string.settings_cache_cleared)
        }
    }

    fun clearCookies() {
        viewModelScope.launch {
            runCatching {
                // WebView cookies flow into every media request via WebViewCookieJar,
                // so wiping them logs the user out of every source at once.
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
            }
            _messages.send(R.string.settings_cookies_cleared)
        }
    }

    fun clearSearchHistory() {
        searchHistoryStore.clearHistory()
        viewModelScope.launch { _messages.send(R.string.settings_search_history_cleared) }
    }
}
