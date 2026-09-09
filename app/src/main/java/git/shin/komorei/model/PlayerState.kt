package git.shin.komorei.model

/**
 * Player state representations for YouTube style minimization and Media3 playback.
 */
enum class PlayerSheetValue {
    EXPANDED,
    COLLAPSED,
    HIDDEN
}

data class PlayerPlaybackState(
    val currentAnime: Anime? = null,
    val currentEpisode: Episode? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val volume: Float = 0.8f, // 0.0 to 1.0
    val brightness: Float = 0.8f, // 0.0 to 1.0
    val playbackSpeed: Float = 1.0f,
    val quality: String = "1080p FHD",
    val isControlsVisible: Boolean = true,
    val sheetValue: PlayerSheetValue = PlayerSheetValue.HIDDEN
)
