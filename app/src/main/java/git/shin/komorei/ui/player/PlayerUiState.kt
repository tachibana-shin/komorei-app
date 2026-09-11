package git.shin.komorei.ui.player

import androidx.media3.common.Tracks
import git.shin.komorei.data.remote.SegmentDataInterceptor
import git.shin.komorei.data.remote.SegmentUrlInterceptor
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode
import git.shin.komorei.model.StreamData
import git.shin.komorei.model.StreamInfo

/**
 * UI State for the video player overlay.
 */
data class PlayerPlaybackState(
    val currentAnime: Anime? = null,
    val currentEpisode: Episode? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isFullscreen: Boolean = false,
    val playbackSpeed: Float = 1f,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sheetValue: PlayerSheetValue = PlayerSheetValue.HIDDEN,

    // Lock mode: disables all controls except unlock button
    val isLocked: Boolean = false,

    // Aspect ratio / Resize mode (matching Media3 AspectRatioFrameLayout.RESIZE_MODE_*)
    val videoResizeMode: Int = 0, // RESIZE_MODE_FIT

    // Tracks: Subtitles and Audio
    val availableTracks: Tracks? = null,
    val selectedAudioTrackId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val isSubtitleEnabled: Boolean = true,

    // Intro/Outro segments
    val introRange: LongRange? = null,
    val outroRange: LongRange? = null,

    // Full (details-upgraded) anime used for stream resolution.
    // List-API anime cards are "Lite" and are upgraded via getAnimeUpdate(needsDetails = true).
    val fullAnime: Anime? = null,

    // Streaming servers available for [currentEpisode], resolved via getStreamList.
    val streams: List<StreamInfo> = emptyList(),
    val selectedStreamId: String? = null,

    // Resolved playback URL + headers, produced by getStream(...) -> StreamData.
    val streamData: StreamData? = null,
    val isLoadingStreams: Boolean = false,
    val streamError: String? = null,

    // Optional per-source media transformers forwarded to the media engine.
    val segmentUrlInterceptor: SegmentUrlInterceptor? = null,
    val segmentDataInterceptor: SegmentDataInterceptor? = null
)

/**
 * Visual states for the swipeable player sheet.
 */
enum class PlayerSheetValue {
    HIDDEN,
    COLLAPSED, // Mini Player
    EXPANDED   // Full UI
}
