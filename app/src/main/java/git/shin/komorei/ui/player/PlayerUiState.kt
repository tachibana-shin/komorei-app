package git.shin.komorei.ui.player

import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode

/**
 * UI State for the video player overlay.
 */
data class PlayerPlaybackState(
    val currentAnime: Anime? = null,
    val currentEpisode: Episode? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sheetValue: PlayerSheetValue = PlayerSheetValue.HIDDEN
)

/**
 * Visual states for the swipeable player sheet.
 */
enum class PlayerSheetValue {
    HIDDEN,
    COLLAPSED, // Mini Player
    EXPANDED   // Full UI
}
