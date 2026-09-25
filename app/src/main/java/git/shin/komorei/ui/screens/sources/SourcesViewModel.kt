package git.shin.komorei.ui.screens.sources

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.data.RepoLoadResult
import git.shin.komorei.data.SourceReposRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.compareVersions
import git.shin.komorei.model.Source
import git.shin.komorei.sdk.KrxManager
import git.shin.komorei.sdk.KrxSourceRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One installed source as rendered in the sources list. */
data class SourceUiState(
    val source: Source,
    val enabled: Boolean,
    /** Index in the pinned list, -1 when not pinned. */
    val pinnedIndex: Int = -1,
    val isUserInstalled: Boolean = false,
    /** Set when an external repo advertises a newer version: "1.2.0". */
    val updateAvailableVersion: String? = null,
)

/** A repo's fetch state used by the "Add source" sheet. */
sealed interface RepoSectionState {
    data object Loading : RepoSectionState

    data class Loaded(
        val name: String,
        val sources: List<ExternalSourceInfo>,
    ) : RepoSectionState

    data object Unavailable : RepoSectionState
}

/**
 * State + actions for the "Nguồn" (Sources) tab — a port of Aidoku's Browse
 * tab: sectioned source list (Updates / Pinned / Installed), search, enable /
 * disable, pin / unpin, uninstall, repo-driven update checks, and the Add
 * source sheet (import .aix/.krx + external sources from repos).
 */
@HiltViewModel
class SourcesViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository,
    private val registry: KrxSourceRegistry,
    private val stateStore: SourceStateStore,
    private val reposRepository: SourceReposRepository,
) : ViewModel() {
    val searchQuery = MutableStateFlow("")

    private val updateMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _installingIds = MutableStateFlow<Set<String>>(emptySet())
    private val _repoStates = MutableStateFlow<Map<String, RepoSectionState>>(emptyMap())

    /** True while a pull-to-refresh gesture on the sources/repos list is in flight. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()

    val installingIds: StateFlow<Set<String>> = _installingIds.asStateFlow()
    val repoStates: StateFlow<Map<String, RepoSectionState>> = _repoStates.asStateFlow()

    /** Installed sources (non-aggregator) — search-filtered, pinned first. */
    val sources: StateFlow<List<SourceUiState>> =
        combine(
            repository.sourcesFlow,
            stateStore.disabled,
            stateStore.pinned,
            searchQuery,
            updateMap,
        ) { all, disabled, pinned, query, updates ->
            val q = query.trim().lowercase()
            all
                .filter { !it.isAggregator }
                .filter {
                    q.isEmpty() ||
                        it.name.lowercase().contains(q) ||
                        it.id.lowercase().contains(q)
                }.map { source ->
                    SourceUiState(
                        source = source,
                        enabled = source.id !in disabled,
                        pinnedIndex = pinned.indexOf(source.id),
                        isUserInstalled = registry.isUserInstalled(source.id),
                        updateAvailableVersion = updates[source.id],
                    )
                }.sortedWith(
                    compareBy<SourceUiState>(
                        { it.pinnedIndex == -1 && it.updateAvailableVersion == null },
                        { if (it.pinnedIndex >= 0) it.pinnedIndex else Int.MAX_VALUE },
                        { it.source.name.lowercase() },
                    ),
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val repos: StateFlow<List<String>> = stateStore.repos

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setEnabled(
        source: Source,
        enabled: Boolean,
    ) {
        stateStore.setDisabled(source.id, !enabled)
        if (enabled) updateMap.update { it - source.id }
    }

    fun togglePinned(source: Source) {
        stateStore.togglePinned(source.id)
    }

    /** Removes a user-installed source (bundled sources are protected). */
    fun uninstall(source: Source) {
        viewModelScope.launch {
            stateStore.unpin(source.id)
            stateStore.setDisabled(source.id, false)
            updateMap.update { it - source.id }
            registry.uninstall(source.id)
        }
    }

    // ── update checks ───────────────────────────────────────────────────────

    /**
     * Fetches every configured repo and marks installed sources that have a
     * newer advertised version (Aidoku's "Updates" section).
     */
    fun checkForUpdates() {
        viewModelScope.launch { performUpdateCheck() }
    }

    /**
     * Pull-to-refresh on the sources list: re-runs the update check with the
     * refresh indicator up until it completes.
     */
    fun refreshSources() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                performUpdateCheck()
            } finally {
                _refreshing.value = false
            }
        }
    }

    private suspend fun performUpdateCheck() {
        val repoUrls = stateStore.repos.value
        if (repoUrls.isEmpty()) {
            sendMessage(R.string.sources_updated_none)
            return
        }
        var found = 0
        repoUrls.forEach { url ->
            when (val result = reposRepository.fetchSourceList(url)) {
                is RepoLoadResult.Success -> {
                    result.repo.sources.forEach { ext ->
                        val installed = repository.sources.firstOrNull { it.id == ext.id } ?: return@forEach
                        if (compareVersions(ext.version, installed.version) > 0) {
                            updateMap.update { it + (ext.id to ext.version) }
                            found++
                        }
                    }
                }
                RepoLoadResult.Unavailable -> Unit
            }
        }
        if (found == 0) sendMessage(R.string.sources_updated_all)
    }

    fun dismissUpdate(source: Source) {
        updateMap.update { it - source.id }
    }

    /**
     * Installs the newest advertised version across all repos for one installed
     * source (the "Update" pill in the Updates section).
     */
    fun updateSource(source: Source) {
        viewModelScope.launch {
            val candidates = mutableListOf<ExternalSourceInfo>()
            stateStore.repos.value.forEach { url ->
                when (val result = reposRepository.fetchSourceList(url)) {
                    is RepoLoadResult.Success ->
                        result.repo.sources
                            .filter { it.id == source.id }
                            .forEach(candidates::add)
                    RepoLoadResult.Unavailable -> Unit
                }
            }
            val best = candidates.maxByOrNull { compareVersions(it.version, source.version) }
            if (best == null || best.downloadURL == null) {
                sendMessage(R.string.sources_external_no_package)
                return@launch
            }
            installExternal(best)
        }
    }

    // ── import & install ────────────────────────────────────────────────────

    /** Installs raw `.krx` / `.aix` bytes picked from the file system. */
    fun importKrx(bytes: ByteArray) {
        viewModelScope.launch {
            val meta = registry.installKrx(bytes)
            when {
                meta == null && krxWouldExist(bytes) -> sendMessage(R.string.sources_import_existing)
                meta == null -> sendMessage(R.string.sources_import_fail)
                else -> {
                    stateStore.setDisabled(meta.id, false)
                    sendMessage(R.string.sources_import_success)
                }
            }
        }
    }

    private fun krxWouldExist(bytes: ByteArray): Boolean {
        val id = KrxManager.readInfo(bytes)?.id ?: return false
        return id in registry.sourceAppList().map { it.id } || id in setOf("all", "local", "komga", "kavita", "suwayomi")
    }

    /** Downloads an external source's package from a repo and installs it. */
    fun installExternal(info: ExternalSourceInfo) {
        if (_installingIds.value.contains(info.id)) return
        _installingIds.update { it + info.id }
        viewModelScope.launch {
            val url = info.downloadURL
            val ok =
                url?.let { reposRepository.downloadPackage(it) }?.let { bytes ->
                    val meta = registry.installKrx(bytes)
                    if (meta != null) {
                        stateStore.setDisabled(meta.id, false)
                        updateMap.update { it - meta.id }
                        true
                    } else {
                        false
                    }
                } ?: false
            _installingIds.update { it - info.id }
            if (ok) {
                sendMessage(R.string.sources_import_success)
            } else {
                sendMessage(R.string.sources_external_get_failed)
            }
        }
    }

    // ── repos ───────────────────────────────────────────────────────────────

    /** Loads (or reloads) one repo's source list into [repoStates]. */
    fun loadRepo(url: String) {
        viewModelScope.launch {
            _repoStates.update { it + (url to RepoSectionState.Loading) }
            when (val result = reposRepository.fetchSourceList(url)) {
                is RepoLoadResult.Success ->
                    _repoStates.update { it + (url to RepoSectionState.Loaded(result.repo.name, result.repo.sources)) }
                RepoLoadResult.Unavailable ->
                    _repoStates.update { it + (url to RepoSectionState.Unavailable) }
            }
        }
    }

    fun refreshAllRepos() {
        stateStore.repos.value.forEach { loadRepo(it) }
    }

    /**
     * Pull-to-refresh on the repo manager: re-fetches every repo **in place**
     * (awaiting each fetch, unlike [refreshAllRepos]'s fire-and-forget) with
     * the refresh indicator up until the last repo lands.
     */
    fun refreshRepos() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                stateStore.repos.value.forEach { url ->
                    _repoStates.update { it + (url to RepoSectionState.Loading) }
                    when (val result = reposRepository.fetchSourceList(url)) {
                        is RepoLoadResult.Success ->
                            _repoStates.update { it + (url to RepoSectionState.Loaded(result.repo.name, result.repo.sources)) }
                        RepoLoadResult.Unavailable ->
                            _repoStates.update { it + (url to RepoSectionState.Unavailable) }
                    }
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun addRepo(rawUrl: String): Boolean {
        val ok = stateStore.addRepo(rawUrl)
        if (!ok) sendMessage(R.string.sources_invalid_url)
        return ok
    }

    fun removeRepo(url: String) {
        stateStore.removeRepo(url)
        _repoStates.update { it - url }
    }

    private fun sendMessage(stringRes: Int) {
        _messages.tryEmit(appContext.getString(stringRes))
    }
}
