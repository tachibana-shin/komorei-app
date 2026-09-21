package git.shin.komorei.ui.screens.search

import git.shin.komorei.model.Anime
import git.shin.komorei.model.Source

/**
 * UI State for multi-source search and discovery.
 *
 * [resultsBySource] maps each source to its found anime (presented
 * as its own section in the UI). [sourceErrors] carries per-source
 * error messages so the UI can show which sources failed. A source
 * absent from both maps was not queried or returned empty results.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(
        val resultsBySource: Map<Source, List<Anime>>,
        val totalCount: Int,
        val sourceErrors: Map<Source, String> = emptyMap(),
    ) : SearchUiState

    data class Error(val message: String) : SearchUiState
}
