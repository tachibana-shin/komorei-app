package git.shin.komorei.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode
import git.shin.komorei.model.StreamInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import git.shin.komorei.model.WatchHistory
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    val allAnimes: List<Anime> = repository.sources.flatMap { src ->
        repository.getFeaturedAnime(src.id) + repository.getSectionsForSource(src.id).values.flatten()
    }.distinctBy { it.id }

    fun openAnime(anime: Anime, episode: Episode? = null) {
        val targetEp = episode ?: anime.episodes.firstOrNull() ?: Episode(
            id = "${anime.id}_ep_1",
            animeId = anime.id,
            sourceId = anime.sourceId,
            episodeNumber = "1",
            title = "Tập 1 - Khởi đầu"
        )

        val durationMs = (targetEp.durationSeconds ?: 1440L) * 1000L

        _playbackState.update { current ->
            current.copy(
                currentAnime = anime,
                currentEpisode = targetEp,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = durationMs,
                sheetValue = PlayerSheetValue.EXPANDED,
                streams = emptyList(),
                selectedStreamId = null,
                streamData = null,
                streamError = null
            )
        }

        // Resolve servers + first stream for the target episode.
        viewModelScope.launch { loadStreams(anime, targetEp) }
    }

    fun setPlayerSheetValue(sheetValue: PlayerSheetValue) {
        _playbackState.update { it.copy(sheetValue = sheetValue) }
    }

    fun togglePlayPause() {
        _playbackState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun seekTo(positionMs: Long) {
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun selectEpisode(episode: Episode) {
        val durationMs = (episode.durationSeconds ?: 1440L) * 1000L
        _playbackState.update { current ->
            current.copy(
                currentEpisode = episode,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = durationMs,
                streams = emptyList(),
                selectedStreamId = null,
                streamData = null,
                streamError = null
            )
        }
        val anime = _playbackState.value.fullAnime ?: _playbackState.value.currentAnime ?: return
        viewModelScope.launch { loadStreams(anime, episode) }
    }

    /**
     * User picks a streaming server for the current episode.
     */
    fun selectStream(stream: StreamInfo) {
        val state = _playbackState.value
        val anime = state.fullAnime ?: state.currentAnime ?: return
        val episode = state.currentEpisode ?: return

        _playbackState.update {
            it.copy(
                selectedStreamId = stream.id,
                streamData = null,
                isLoadingStreams = true,
                streamError = null
            )
        }

        viewModelScope.launch {
            runCatching { repository.getStream(anime, episode, stream) }
                .onSuccess { resolved ->
                    _playbackState.update {
                        it.copy(
                            streamData = resolved,
                            selectedStreamId = stream.id,
                            isLoadingStreams = false,
                            streamError = null
                        )
                    }
                }
                .onFailure { e ->
                    _playbackState.update {
                        it.copy(isLoadingStreams = false, streamError = e.message ?: "Lỗi tải nguồn phát")
                    }
                }
        }
    }

    /**
     * Resolve the streaming servers for [episode].
     *
     * The list-API [anime] is a Lite card with almost no data, so per source contract we
     * upgrade it via getAnimeUpdate(needsDetails = true, needsChapters = false) before
     * asking the source for servers. The first server is auto-resolved via getStream(...).
     */
    private suspend fun loadStreams(anime: Anime, episode: Episode) {
        _playbackState.update { it.copy(isLoadingStreams = true, streamError = null) }
        runCatching {
            val full = repository.getAnimeUpdate(anime, needsDetails = true, needsChapters = false)
            val streams = repository.getStreamList(full, episode)
            val first = streams.firstOrNull()
            val resolved = first?.let { repository.getStream(full, episode, it) }

            _playbackState.update {
                it.copy(
                    fullAnime = full,
                    streams = streams,
                    selectedStreamId = first?.id,
                    streamData = resolved,
                    isLoadingStreams = false,
                    streamError = null,
                    segmentUrlInterceptor = repository.segmentUrlInterceptor,
                    segmentDataInterceptor = repository.segmentDataInterceptor
                )
            }
        }.onFailure { e ->
            _playbackState.update {
                it.copy(isLoadingStreams = false, streamError = e.message ?: "Lỗi tải nguồn phát")
            }
        }
    }

    /**
     * Re-resolves servers + first stream after a failure (streamError shown in the UI).
     */
    fun retryStreams() {
        val state = _playbackState.value
        val anime = state.fullAnime ?: state.currentAnime ?: return
        val episode = state.currentEpisode ?: return
        viewModelScope.launch { loadStreams(anime, episode) }
    }

    fun dismissPlayer() {
        _playbackState.update {
            it.copy(
                isPlaying = false,
                sheetValue = PlayerSheetValue.HIDDEN
            )
        }
    }

    fun toggleBookmark(animeId: String) {
        // Handled by AnimeDetailViewModel or LibraryViewModel now
    }
}