package git.shin.komorei.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.Episode
import git.shin.komorei.model.WatchHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnimeDetailUiState(
    val masterAnime: Anime? = null, 
    val selectedSeason: AnimeSeason? = null,
    val currentSeasonEpisodes: List<Episode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val sourceName: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeDetailUiState())
    val uiState: StateFlow<AnimeDetailUiState> = _uiState.asStateFlow()

    // Observe bookmark status for the master anime
    val isBookmarked: StateFlow<Boolean> = _uiState
        .flatMapLatest { state ->
            val anime = state.masterAnime ?: return@flatMapLatest flowOf(false)
            libraryRepository.bookmarkedAnimes.map { list ->
                list.any { it.id == anime.id && it.sourceId == anime.sourceId }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Lazy observe history based on the current season's animeId
    val watchHistory: StateFlow<List<WatchHistory>> = _uiState
        .flatMapLatest { state ->
            val anime = state.masterAnime ?: return@flatMapLatest flowOf(emptyList())
            val seasonId = state.selectedSeason?.animeId ?: anime.id
            
            libraryRepository.getWatchHistoryForAnime(seasonId, anime.sourceId)
                .map { entities ->
                    entities.map { entity ->
                        WatchHistory(
                            animeId = entity.animeId,
                            sourceId = entity.sourceId,
                            episodeId = entity.episodeId,
                            progressMs = entity.progressMs,
                            durationMs = entity.durationMs,
                            lastWatchedAt = entity.lastWatchedAt
                        )
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Initial entry point: Upgrades Lite anime to Full metadata + first season episodes.
     */
    fun loadInitialData(liteAnime: Anime) {
        if (_uiState.value.masterAnime?.id == liteAnime.id) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            
            // 1. Fetch FULL metadata (including all available seasons)
            val fullMetadata = animeRepository.getAnimeUpdate(liteAnime, needsDetails = true, needsChapters = false)
            val sourceName = animeRepository.getSourceName(liteAnime.sourceId)

            // 2. Select initial season (usually the one provided by liteAnime or the first in list)
            val initialSeason = fullMetadata.seasons.find { it.animeId == liteAnime.id } 
                ?: fullMetadata.seasons.firstOrNull() 
                ?: AnimeSeason(liteAnime.id, "Full Season")
                
            _uiState.update { it.copy(masterAnime = fullMetadata, selectedSeason = initialSeason, sourceName = sourceName) }
            
            // 3. Fetch episodes for THIS specific season
            fetchEpisodesForSeason(initialSeason, liteAnime.sourceId)
        }
    }

    /**
     * Switches season: Upgrades a minimal Anime object (ID/Source) to its chapter list.
     */
    fun selectSeason(season: AnimeSeason) {
        val master = _uiState.value.masterAnime ?: return
        if (_uiState.value.selectedSeason?.animeId == season.animeId) return
        
        _uiState.update { it.copy(selectedSeason = season, currentSeasonEpisodes = emptyList()) }
        
        viewModelScope.launch {
            fetchEpisodesForSeason(season, master.sourceId)
        }
    }

    private suspend fun fetchEpisodesForSeason(season: AnimeSeason, sourceId: String) {
        _uiState.update { it.copy(isLoadingEpisodes = true) }
        
        // Reconstruct minimal Anime for the update call
        val liteSeasonAnime = Anime(
            id = season.animeId,
            sourceId = sourceId,
            title = "",
            originalTitle = "",
            posterUrl = "",
            bannerUrl = "",
            description = "",
            episodeCount = 0,
            currentEpisode = null,
            rating = null,
            ratingCount = null,
            status = AnimeStatus.UNKNOWN,
            releaseYear = null,
            genres = emptyList(),
            authors = emptyList(),
            studio = null,
            seasonOf = null
        )
        
        val updatedWithChapters = animeRepository.getAnimeUpdate(
            liteSeasonAnime, 
            needsDetails = false, 
            needsChapters = true
        )
        
        _uiState.update { 
            it.copy(
                currentSeasonEpisodes = updatedWithChapters.episodes,
                isLoadingEpisodes = false
            ) 
        }
    }

    fun toggleBookmark() {
        val anime = _uiState.value.masterAnime ?: return
        viewModelScope.launch {
            libraryRepository.toggleBookmark(anime)
        }
    }
}
