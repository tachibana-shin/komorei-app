package git.shin.komorei.ui.screens.rankings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RankingPeriod { DAY, WEEK, MONTH }

/** An anime plus its 1-based position in the current ranking. */
data class RankedAnime(
    val rank: Int,
    val anime: Anime,
)

data class RankingsUiState(
    val isLoading: Boolean = true,
    val period: RankingPeriod = RankingPeriod.DAY,
    val items: List<RankedAnime> = emptyList(),
)

/**
 * Top-anime rankings by day / week / month.
 *
 * All data is fake (as everywhere else) — every period sorts the same catalogue
 * with a different key so the tabs actually look different. A real backend just
 * replaces [load].
 */
@HiltViewModel
class RankingsViewModel @Inject constructor(
    private val repository: AnimeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingsUiState())
    val uiState: StateFlow<RankingsUiState> = _uiState.asStateFlow()

    private var catalogue: List<Anime> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            catalogue = runCatching { repository.allAnimes() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                items = rankAnimes(catalogue, _uiState.value.period),
            )
        }
    }

    fun selectPeriod(period: RankingPeriod) {
        _uiState.value = _uiState.value.copy(
            period = period,
            items = rankAnimes(catalogue, period),
        )
    }

    /** Source display name for a card's badge (never the raw source id). */
    fun getSourceName(sourceId: String): String = repository.getSourceName(sourceId)
}

/** Deterministic per-period ordering — pure, so it is unit-testable. */
internal fun rankAnimes(source: List<Anime>, period: RankingPeriod): List<RankedAnime> {
    val sorted = when (period) {
        RankingPeriod.DAY -> source.sortedByDescending { it.views }
        RankingPeriod.WEEK -> source.sortedWith(
            compareByDescending<Anime> { it.rating ?: 0f }.thenByDescending { it.views }
        )

        RankingPeriod.MONTH -> source.sortedWith(
            compareByDescending<Anime> { it.episodeCount }.thenByDescending { it.views }
        )
    }
    return sorted.take(MAX_ENTRIES).mapIndexed { index, anime -> RankedAnime(index + 1, anime) }
}

private const val MAX_ENTRIES = 50
