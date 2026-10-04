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
    /**
     * True from the moment an anime is requested until [masterAnime] holds its
     * full metadata, and the detail screen must render a skeleton for that whole
     * window.
     *
     * The `Anime` handed to the detail screen comes from a listing API, so it is
     * a **Lite** record: a title, a poster, and little else. Falling back to it
     * while the upgrade is in flight — which is what `masterAnime ?: anime` did —
     * puts a real-looking screen on display with no studio, no genres, no seasons,
     * no episodes and a zeroed rating. It reads as broken data rather than as
     * loading, which is the one thing a skeleton exists to prevent.
     */
    val isLoadingMetadata: Boolean = true,
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
    val sourceName: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnimeDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val animeRepository: AnimeRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnimeDetailUiState())
    val uiState: StateFlow<AnimeDetailUiState> = _uiState.asStateFlow()

    // Remembers the Lite anime for retry when the initial metadata fetch failed.
    private var lastLiteAnime: Anime? = null

    /**
     * Id of the anime the current load belongs to. Re-entering the detail screen
     * for the same anime is a no-op, so the record is kept — and re-entering it
     * shows the data already in memory instead of re-fetching. Distinct from
     * `masterAnime?.id`, which a failed load leaves null and which therefore
     * cannot tell "never requested" from "requested and failed".
     */
    private var requestedAnimeId: String? = null

    /**
     * Cache of already-fetched episode lists keyed by season [AnimeSeason.animeId].
     * Lets switching back to a previously visited season render instantly from
     * memory instead of re-fetching from the source (youtube-like behavior).
     */
    private val seasonEpisodesCache: MutableMap<String, List<Episode>> = mutableMapOf()

    // Observe bookmark status for the master anime
    val isBookmarked: StateFlow<Boolean> =
        _uiState
            .flatMapLatest { state ->
                val anime = state.masterAnime ?: return@flatMapLatest flowOf(false)
                libraryRepository.bookmarkedAnimes.map { list ->
                    list.any { it.id == anime.id && it.sourceId == anime.sourceId }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Lazy observe history based on the current season's animeId
    val watchHistory: StateFlow<List<WatchHistory>> =
        _uiState
            .flatMapLatest { state ->
                val anime = state.masterAnime ?: return@flatMapLatest flowOf(emptyList())
                val seasonId = anime.id
                libraryRepository
                    .getWatchHistoryForAnime(seasonId, anime.sourceId)
                    .map { entities ->
                        entities.map { entity ->
                            WatchHistory(
                                animeId = entity.animeId,
                                sourceId = entity.sourceId,
                                episodeId = entity.episodeId,
                                progressMs = entity.progressMs,
                                durationMs = entity.durationMs,
                                lastWatchedAt = entity.lastWatchedAt,
                            )
                        }
                    }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Initial entry point: takes the FULL anime and serves metadata + first
     * season episodes out of it, fetching only what it was not handed.
     *
     * [fullAnime] is the upgrade the player already performed — `loadStreams`
     * asks for `needsDetails` **and** `needsChapters` in a single call, so its
     * result carries the seasons *and* this season's episodes. Passing it in
     * here is what removes the duplicate round trips: this ViewModel used to ask
     * the source for the same anime twice more (metadata, then chapters), on top
     * of the player's, and every extra call is another full source fetch — on a
     * source behind a JS challenge that is seconds of skeleton each time.
     *
     * With [fullAnime] null (the detail screen reached without going through the
     * player, or after the player's own load failed) it falls back to fetching
     * the metadata itself, so nothing regresses.
     */
    fun loadInitialData(
        liteAnime: Anime,
        fullAnime: Anime? = null,
    ) {
        if (requestedAnimeId == liteAnime.id) return
        requestedAnimeId = liteAnime.id
        lastLiteAnime = liteAnime

        // Synchronously, before returning: the previous anime's master record and
        // the incoming Lite card both have to be out of the UI state now, or the
        // header renders one of them for a frame before the coroutine even runs.
        _uiState.update {
            it.copy(
                masterAnime = null,
                isLoadingMetadata = true,
                selectedSeason = null,
                fullSeasonEpisodes = emptyList(),
                virtualSeasons = emptyList(),
                selectedVirtualSeasonId = null,
                currentSeasonEpisodes = emptyList(),
                isLoadingEpisodes = true,
                episodeError = null,
            )
        }

        // No id check on [fullAnime] on purpose. It comes from the player, which
        // only ever holds the record for the anime it is playing, so it is the
        // right record by construction. Checking its id against [liteAnime] used
        // to reject it — AnimeVietsub returns an upgraded record with an EMPTY id
        // (`parse_detail` builds one without a key and the SDK's `copy_from`
        // assigns it unconditionally) — and the rejection sent this screen off to
        // fetch the same anime a second time, which is the whole thing the reuse
        // exists to avoid. Second page load, different episode handles, episode
        // strip highlights nothing.
        if (fullAnime != null) {
            applyFullAnime(fullAnime, liteAnime.sourceId, liteAnime.id)
            return
        }

        viewModelScope.launch {
            // 1. Fetch FULL metadata (including all available seasons)
            runCatching {
                animeRepository.getAnimeUpdate(liteAnime, needsDetails = true, needsChapters = false)
            }.onSuccess { fetched ->
                applyFullAnime(fetched, liteAnime.sourceId, liteAnime.id)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoadingMetadata = false,
                        isLoadingEpisodes = false,
                        episodeError = e.message ?: appContext.getString(R.string.error_load_data),
                    )
                }
            }
        }
    }

    /**
     * Adopts [full] as the master record and loads the episodes of its initial
     * season, seeding [seasonEpisodesCache] from [full] first so a full anime
     * that already carries chapters costs no second call.
     *
     * [animeId] is the id this screen was asked for, and it is deliberately NOT
     * read back off [full]. An upgraded record's own `id` is whatever the source
     * felt like returning: AnimeVietsub's `parse_detail` builds a fresh record
     * without a key, and the SDK's `copy_from` assigns it unconditionally, so the
     * key comes back empty. Matching the season list against `full.id` then finds
     * nothing and silently falls back to the FIRST season — which, on a
     * multi-season title, is a different season from the one the player opened.
     * The detail screen loads that season's episodes, none of whose keys match
     * the playing episode, and the episode strip highlights nothing.
     */
    private fun applyFullAnime(
        full: Anime,
        sourceId: String,
        animeId: String,
    ) {
        val sourceName = animeRepository.getSourceName(sourceId)

        // Select initial season (the one this screen was opened for, else the first)
        val initialSeason =
            full.seasons.find { it.animeId == animeId }
                ?: full.seasons.firstOrNull()
                ?: AnimeSeason(animeId, appContext.getString(R.string.season_fallback_full))

        // `getAnimeUpdate(needsChapters = true)` already put this season's
        // episodes on the record, so the season fetch resolves from memory.
        if (full.episodes.isNotEmpty()) {
            seasonEpisodesCache[animeId] = full.episodes
        }

        _uiState.update {
            it.copy(
                masterAnime = full,
                isLoadingMetadata = false,
                selectedSeason = initialSeason,
                sourceName = sourceName,
            )
        }

        // Fire-and-forget: the episode list is published through the state flow.
        // Launched rather than awaited so this stays callable from the synchronous
        // path too, where the player already holds the full anime.
        viewModelScope.launch { fetchEpisodesForSeason(initialSeason, sourceId) }
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
            val chunk =
                state.fullSeasonEpisodes
                    .chunked(VIRTUAL_SEASON_MAX_EPISODES)
                    .getOrNull(chunkIndex) ?: return
            _uiState.update {
                it.copy(
                    selectedVirtualSeasonId = season.id,
                    currentSeasonEpisodes = chunk,
                    episodeError = null,
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
                episodeError = null,
            )
        }

        viewModelScope.launch {
            fetchEpisodesForSeason(season, master.sourceId)
        }
    }

    private suspend fun fetchEpisodesForSeason(
        season: AnimeSeason,
        sourceId: String,
    ) {
        // Serve previously fetched list instantly (cache hit path: no loading flash).
        val cached = seasonEpisodesCache[season.animeId]
        if (cached != null) {
            applySeasonEpisodes(season, cached)
            return
        }

        _uiState.update { it.copy(isLoadingEpisodes = true, episodeError = null) }

        // Reconstruct minimal Anime for the update call
        val liteSeasonAnime =
            Anime(
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
                seasonOf = null,
            )

        runCatching {
            animeRepository.getAnimeUpdate(
                liteSeasonAnime,
                needsDetails = false,
                needsChapters = true,
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
                    episodeError = e.message ?: appContext.getString(R.string.error_load_data),
                )
            }
        }
    }

    /**
     * Applies a (possibly cached) full episode list for [season]: splits it into
     * 50-episode virtual seasons when huge and updates all related UI state.
     */
    private fun applySeasonEpisodes(
        season: AnimeSeason,
        full: List<Episode>,
    ) {
        val chunks = full.chunked(VIRTUAL_SEASON_MAX_EPISODES)
        // Split huge seasons (e.g. Conan, 1000+ eps) into 50-episode "virtual seasons".
        val virtual =
            if (chunks.size > 1) {
                chunks.mapIndexed { index, chunk ->
                    AnimeSeason(
                        animeId = season.animeId,
                        title =
                            appContext.getString(
                                R.string.virtual_season_title_format,
                                chunk.first().episodeNumber,
                                chunk.last().episodeNumber,
                            ),
                        id = "${season.animeId}#$index",
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
                currentSeasonEpisodes =
                    virtual.firstOrNull()?.run {
                        chunks[id.substringAfterLast('#').toInt()]
                    } ?: full,
                isLoadingEpisodes = false,
                episodeError = null,
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
                // Drop the request guard first, or the retry early-returns on the
                // very id it is being asked to retry.
                requestedAnimeId = null
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
