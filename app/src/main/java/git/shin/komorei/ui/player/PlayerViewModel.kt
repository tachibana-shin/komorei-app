package git.shin.komorei.ui.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode
import git.shin.komorei.model.PlayerPlaybackState
import git.shin.komorei.model.PlayerSheetValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    private val _bookmarkedAnimeIds = MutableStateFlow<Set<String>>(
        setOf("solo_leveling_s2", "frieren_journey")
    )
    val bookmarkedAnimeIds: StateFlow<Set<String>> = _bookmarkedAnimeIds.asStateFlow()

    val allAnimes: List<Anime> = repository.sources.flatMap { src ->
        repository.getFeaturedAnime(src.id) + repository.getSectionsForSource(src.id).values.flatten()
    }.distinctBy { it.id }

    fun openAnime(anime: Anime, episode: Episode? = null) {
        val targetEp = episode ?: anime.episodes.firstOrNull() ?: Episode(
            id = "${anime.id}_ep_1",
            animeId = anime.id,
            sourceId = anime.sourceId,
            episodeNumber = "1",
            title = "Tập 1 - Khởi đầu",
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )

        _playbackState.update { current ->
            current.copy(
                currentAnime = anime,
                currentEpisode = targetEp,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = targetEp.durationSeconds * 1000L,
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
        _playbackState.update { current ->
            current.copy(
                currentEpisode = episode,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = episode.durationSeconds * 1000L
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
        _bookmarkedAnimeIds.update { set ->
            if (set.contains(animeId)) set - animeId else set + animeId
        }
    }
}
