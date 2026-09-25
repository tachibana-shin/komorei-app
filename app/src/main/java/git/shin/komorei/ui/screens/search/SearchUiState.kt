package git.shin.komorei.ui.screens.search

import git.shin.komorei.model.Anime
import git.shin.komorei.model.Source

/**
 * UI State for multi-source search and discovery.
 *
 * [Searching] is the live phase: every candidate source renders its own
 * section (header + horizontal row) the moment the search starts, and each
 * section transitions to results/error independently as its [SourceSearchEvent]
 * arrives — a slow source never blocks the sections that already finished.
 * [resultsBySource] maps each source to its found anime; [sourceErrors] carries
 * per-source error messages; [emptySources] tracks sources that finished
 * cleanly with zero matches (a section must still show "không có kết quả"
 * instead of silently disappearing or shimmering forever). [Success] is the
 * terminal all-done state.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState

    data class Searching(
        val candidateSources: List<Source>,
        val resultsBySource: Map<Source, List<Anime>> = emptyMap(),
        val sourceErrors: Map<Source, String> = emptyMap(),
        val emptySources: Set<Source> = emptySet(),
    ) : SearchUiState

    data class Success(
        val resultsBySource: Map<Source, List<Anime>>,
        val totalCount: Int,
        val sourceErrors: Map<Source, String> = emptyMap(),
        val emptySources: Set<Source> = emptySet(),
    ) : SearchUiState

    data class Error(
        val message: String,
    ) : SearchUiState
}
