package git.shin.komorei.ui.screens.sources

import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.data.compareVersions

/**
 * The flat external-source catalog behind the "Thêm nguồn" sheet — a port of
 * Aidoku's `AddSourceView`: every configured repo contributes its sources to
 * ONE merged list, source ids are deduped across repos (the newest advertised
 * version wins, mirroring `BrowseViewModel.loadExternalSources`'s
 * `sourceById` merge), and any per-repo fetch state is summarized into the
 * [loading]/[failed] flags so the UI can show one coherent state instead of
 * per-repo expansion rows.
 */
data class ExternalCatalog(
    val sources: List<ExternalSourceInfo>,
    val hasRepos: Boolean,
    val loading: Boolean,
    val failed: Boolean,
)

/**
 * Merges the per-repo [RepoSectionState] map into a single deduped catalog.
 * Order follows Aidoku: sources are sorted by name. [loading] is true while
 * any configured repo has not resolved yet (null or [RepoSectionState.Loading]);
 * [failed] records whether at least one repo ended in [RepoSectionState.Unavailable].
 */
fun buildExternalCatalog(
    repos: List<String>,
    states: Map<String, RepoSectionState>,
): ExternalCatalog {
    if (repos.isEmpty()) {
        return ExternalCatalog(sources = emptyList(), hasRepos = false, loading = false, failed = false)
    }
    val byId = linkedMapOf<String, ExternalSourceInfo>()
    for (url in repos) {
        val state = states[url] as? RepoSectionState.Loaded ?: continue
        for (info in state.sources) {
            val prev = byId[info.id]
            if (prev == null || compareVersions(info.version, prev.version) > 0) {
                byId[info.id] = info
            }
        }
    }
    val loading = repos.any { states[it] == null || states[it] is RepoSectionState.Loading }
    val failed = repos.any { states[it] is RepoSectionState.Unavailable }
    return ExternalCatalog(
        sources = byId.values.sortedBy { it.name.lowercase() },
        hasRepos = true,
        loading = loading,
        failed = failed,
    )
}