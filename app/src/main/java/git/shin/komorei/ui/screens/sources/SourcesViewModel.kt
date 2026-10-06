package git.shin.komorei.ui.screens.sources

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.data.LogStore
import git.shin.komorei.data.RepoLoadResult
import git.shin.komorei.data.SourceReposRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.compareVersions
import git.shin.komorei.model.Source
import git.shin.komorei.sdk.KrxManager
import git.shin.komorei.sdk.KrxSourceRegistry
import kotlinx.coroutines.CancellationException
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

    /**
     * Bulk uninstall for the selection bar: drops every USER-INSTALLED source
     * in [items] — and says so when nothing qualified, which the old filter
     * didn't: selecting only bundled sources cleared the selection and showed
     * no dialog, no toast, nothing.
     */
    fun uninstallSelected(items: List<SourceUiState>) {
        val targets = items.filter { it.isUserInstalled }
        if (targets.isEmpty()) {
            sendMessage(R.string.sources_uninstall_bundled_hint)
            return
        }
        targets.forEach { uninstall(it.source) }
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
        val found = linkedMapOf<String, String>()
        var checked = false
        repoUrls.forEach { url ->
            when (val result = reposRepository.fetchSourceList(url)) {
                is RepoLoadResult.Success -> {
                    checked = true
                    result.repo.sources.forEach { ext ->
                        val installed = repository.sources.firstOrNull { it.id == ext.id } ?: return@forEach
                        if (compareVersions(ext.version, installed.version) > 0) {
                            found[ext.id] = ext.version
                        }
                    }
                }
                RepoLoadResult.Unavailable -> Unit
            }
        }
        // Rebuild, never accumulate: a repo that dropped an advertised version
        // — or went down after an earlier success — must not keep its
        // "Cập nhật" pill for the rest of the session. When NO repo answered
        // this pass the previous pills are left alone: that is a fetch
        // failure, not a "nothing to update".
        if (checked) {
            updateMap.value = found
            if (found.isEmpty()) sendMessage(R.string.sources_updated_all)
        }
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
            LogStore.info("install requested: ${info.id} from $url", "sources")
            // The spinner is cleared in `finally`, and the body is guarded.
            //
            // `downloadPackage` already folds its own failures into `null`, so
            // the throw that mattered came from `installKrx`: it writes a file
            // and instantiates the wasm, and a package that turns out to be
            // truncated or corrupt aborts in the middle of that. The id was only
            // removed on the line after the work, so the throw skipped it and the
            // row read "đang cài" for the rest of the process — with the guard
            // above then turning every later tap into a silent no-op. Restarting
            // the app was the only way to clear it.
            val result =
                try {
                    url?.let { reposRepository.downloadPackage(it) }?.let { bytes ->
                        // Read before installing: after it, the source is
                        // user-installed either way, and `updateMap` has just been
                        // cleared — so anything sampled afterwards says "replaced"
                        // about every install.
                        val replaced = registry.isUserInstalled(info.id)
                        val meta = registry.installKrx(bytes)
                        if (meta != null) {
                            stateStore.setDisabled(meta.id, false)
                            updateMap.update { it - meta.id }
                            LogStore.info(
                                if (replaced) "updated ${meta.id}" else "installed ${meta.id}",
                                "sources",
                            )
                            InstallResult.Done(replaced)
                        } else {
                            LogStore.error("package rejected: ${info.id}", "sources")
                            InstallResult.Rejected
                        }
                    } ?: run {
                        LogStore.error("no download URL for ${info.id}", "sources")
                        InstallResult.Rejected
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogStore.error("install failed for ${info.id}: ${e.message}", "sources")
                    InstallResult.Rejected
                } finally {
                    _installingIds.update { it - info.id }
                }
            when (result) {
                is InstallResult.Done ->
                    sendMessage(
                        if (result.replaced) R.string.sources_update_success else R.string.sources_import_success,
                    )
                is InstallResult.Rejected -> sendMessage(R.string.sources_external_get_failed)
            }
        }
    }

    /**
     * How an install ended. [replaced] distinguishes a fresh install from an
     * upgrade of a source that was already there, so the two can say so — the
     * single "installed" message was wrong for an update, and the generic
     * failure message for a failed update said nothing about the reader's
     * existing source having been kept.
     */
    private sealed interface InstallResult {
        data class Done(
            val replaced: Boolean,
        ) : InstallResult

        data object Rejected : InstallResult
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
