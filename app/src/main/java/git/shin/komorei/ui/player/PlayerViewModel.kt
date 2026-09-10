package git.shin.komorei.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode
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
                sheetValue = PlayerSheetValue.EXPANDED
            )
        }
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
                durationMs = durationMs
            )
        }
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
