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
import git.shin.komorei.data.update.UpdateCheckResult
import git.shin.komorei.data.update.UpdateInfo
import git.shin.komorei.data.update.UpdateManager
import git.shin.komorei.data.update.UpdateUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val updateManager: UpdateManager,
) : ViewModel() {
    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** One-shot string-resource ids to surface as a Toast. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    val appVersion: String = BuildConfig.VERSION_NAME

    fun checkForUpdate() {
        if (_updateState.value is UpdateUiState.Checking ||
            _updateState.value is UpdateUiState.Downloading
        ) {
            return
        }

        viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            updateManager.checkForUpdate().fold(
                onSuccess = { result ->
                    when (result) {
                        UpdateCheckResult.UpToDate -> {
                            _updateState.value = UpdateUiState.Idle
                            _messages.send(R.string.settings_update_latest)
                        }
                        is UpdateCheckResult.Available -> {
                            _updateState.value = UpdateUiState.Available(result.info)
                        }
                    }
                },
                onFailure = {
                    _updateState.value = UpdateUiState.Idle
                    _messages.send(R.string.settings_update_failed)
                },
            )
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        if (_updateState.value is UpdateUiState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading(info, 0)
            try {
                updateManager.downloadAndInstall(info) { progress ->
                    _updateState.value = UpdateUiState.Downloading(info, progress)
                }
                _messages.send(R.string.settings_update_install_started)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _messages.send(R.string.settings_update_failed)
            } finally {
                _updateState.value = UpdateUiState.Idle
            }
        }
    }

    fun dismissUpdate() {
        if (_updateState.value is UpdateUiState.Available) {
            _updateState.value = UpdateUiState.Idle
        }
    }

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
