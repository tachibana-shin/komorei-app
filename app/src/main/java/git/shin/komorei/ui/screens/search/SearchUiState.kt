package git.shin.komorei.ui.screens.search

import git.shin.komorei.model.Anime
import git.shin.komorei.model.Source

/**
 * UI State for multi-source search and discovery.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(
        val resultsBySource: Map<Source, List<Anime>>,
        val totalCount: Int
    ) : SearchUiState

    data class Error(val message: String) : SearchUiState
}
