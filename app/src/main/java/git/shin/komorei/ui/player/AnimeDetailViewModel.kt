package git.shin.komorei.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
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
    /** Full episode list of the selected real season, before virtual-season splitting. */
    val fullSeasonEpisodes: List<Episode> = emptyList(),
    /** 50-episode chunks of the selected real season. Empty when it has <= [AnimeDetailViewModel.VIRTUAL_SEASON_MAX_EPISODES]. */
    val virtualSeasons: List<AnimeSeason> = emptyList(),
    /** Id of the selected virtual season (chunk). Null when no split is active. */
    val selectedVirtualSeasonId: String? = null,
    val currentSeasonEpisodes: List<Episode> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val episodeError: String? = null,
    val sourceName: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val animeRepository: AnimeRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeDetailUiState())
    val uiState: StateFlow<AnimeDetailUiState> = _uiState.asStateFlow()

    // Remembers the Lite anime for retry when the initial metadata fetch failed.
    private var lastLiteAnime: Anime? = null

    /**
     * Cache of already-fetched episode lists keyed by season [AnimeSeason.animeId].
     * Lets switching back to a previously visited season render instantly from
     * memory instead of re-fetching from the source (youtube-like behavior).
     */
    private val seasonEpisodesCache: MutableMap<String, List<Episode>> = mutableMapOf()

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
        lastLiteAnime = liteAnime
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true, episodeError = null) }
            
            // 1. Fetch FULL metadata (including all available seasons)
            runCatching {
                animeRepository.getAnimeUpdate(liteAnime, needsDetails = true, needsChapters = false)
            }.onSuccess { fullMetadata ->
                val sourceName = animeRepository.getSourceName(liteAnime.sourceId)

                // 2. Select initial season (usually the one provided by liteAnime or the first in list)
                val initialSeason = fullMetadata.seasons.find { it.animeId == liteAnime.id }
                    ?: fullMetadata.seasons.firstOrNull()
                    ?: AnimeSeason(
                        liteAnime.id,
                        appContext.getString(R.string.season_fallback_full)
                    )

                _uiState.update {
                    it.copy(masterAnime = fullMetadata, selectedSeason = initialSeason, sourceName = sourceName)
                }

                // 3. Fetch episodes for THIS specific season
                fetchEpisodesForSeason(initialSeason, liteAnime.sourceId)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoadingEpisodes = false,
                        episodeError = e.message ?: appContext.getString(R.string.error_load_data)
                    )
                }
            }
        }
    }

    /**
     * Switches season. Real seasons (different animeId) trigger a fetch; virtual
     * seasons (50-episode chunks of the current real season) switch locally.
     * Previously fetched seasons are served from [seasonEpisodesCache].
     */
    fun selectSeason(season: AnimeSeason) {
        val state = _uiState.value
        val master = state.masterAnime ?: return

        // Virtual chunk switch — fully local, no network.
        if (state.virtualSeasons.any { it.id == season.id }) {
            val chunkIndex = season.id.substringAfterLast('#').toIntOrNull() ?: return
            val chunk = state.fullSeasonEpisodes
                .chunked(VIRTUAL_SEASON_MAX_EPISODES)
                .getOrNull(chunkIndex) ?: return
            _uiState.update {
                it.copy(
                    selectedVirtualSeasonId = season.id,
                    currentSeasonEpisodes = chunk,
                    episodeError = null
                )
            }
            return
        }

        // Already-fetched season (incl. re-selecting the current one): render from cache.
        val cached = seasonEpisodesCache[season.animeId]
        if (cached != null) {
            applySeasonEpisodes(season, cached)
            return
        }

        // Real season switch.
        if (state.selectedSeason?.animeId == season.animeId) return

        _uiState.update {
            it.copy(
                selectedSeason = season,
                fullSeasonEpisodes = emptyList(),
                virtualSeasons = emptyList(),
                selectedVirtualSeasonId = null,
                currentSeasonEpisodes = emptyList(),
                episodeError = null
            )
        }

        viewModelScope.launch {
            fetchEpisodesForSeason(season, master.sourceId)
        }
    }

    private suspend fun fetchEpisodesForSeason(season: AnimeSeason, sourceId: String) {
        // Serve previously fetched list instantly (cache hit path: no loading flash).
        val cached = seasonEpisodesCache[season.animeId]
        if (cached != null) {
            applySeasonEpisodes(season, cached)
            return
        }

        _uiState.update { it.copy(isLoadingEpisodes = true, episodeError = null) }
        
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
        
        runCatching {
            animeRepository.getAnimeUpdate(
                liteSeasonAnime,
                needsDetails = false,
                needsChapters = true
            )
        }.onSuccess { updatedWithChapters ->
            val full = updatedWithChapters.episodes
            seasonEpisodesCache[season.animeId] = full
            applySeasonEpisodes(season, full)
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    currentSeasonEpisodes = emptyList(),
                    isLoadingEpisodes = false,
                    episodeError = e.message ?: appContext.getString(R.string.error_load_data)
                )
            }
        }
    }

    /**
     * Applies a (possibly cached) full episode list for [season]: splits it into
     * 50-episode virtual seasons when huge and updates all related UI state.
     */
    private fun applySeasonEpisodes(season: AnimeSeason, full: List<Episode>) {
        val chunks = full.chunked(VIRTUAL_SEASON_MAX_EPISODES)
        // Split huge seasons (e.g. Conan, 1000+ eps) into 50-episode "virtual seasons".
        val virtual = if (chunks.size > 1) {
            chunks.mapIndexed { index, chunk ->
                AnimeSeason(
                    animeId = season.animeId,
                    title = appContext.getString(
                        R.string.virtual_season_title_format,
                        chunk.first().episodeNumber,
                        chunk.last().episodeNumber
                    ),
                    id = "${season.animeId}#$index"
                )
            }
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                selectedSeason = season,
                fullSeasonEpisodes = full,
                virtualSeasons = virtual,
                selectedVirtualSeasonId = virtual.firstOrNull()?.id,
                currentSeasonEpisodes = virtual.firstOrNull()?.run {
                    chunks[id.substringAfterLast('#').toInt()]
                } ?: full,
                isLoadingEpisodes = false,
                episodeError = null
            )
        }
    }

    /**
     * Re-runs the last failed load: full metadata if it never arrived, otherwise the
     * current season's episode list.
     */
    fun retryEpisodes() {
        val state = _uiState.value
        val master = state.masterAnime
        when {
            master == null -> {
                lastLiteAnime?.let { loadInitialData(it) }
            }
            state.selectedSeason != null -> {
                val season = state.selectedSeason
                viewModelScope.launch { fetchEpisodesForSeason(season, master.sourceId) }
            }
        }
    }

    fun toggleBookmark() {
        val anime = _uiState.value.masterAnime ?: return
        viewModelScope.launch {
            libraryRepository.toggleBookmark(anime)
        }
    }

    companion object {
        /** Episodes per virtual season when a real season has too many to display at once. */
        private const val VIRTUAL_SEASON_MAX_EPISODES = 50
    }
}
