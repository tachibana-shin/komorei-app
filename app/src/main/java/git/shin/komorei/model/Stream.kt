package git.shin.komorei.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StreamInfo(
    val id: String,
    val name: String,
    val quality: String
)

/**
 * A millisecond time range — used to mark an episode's opening (intro) or ending
 * (outro) segment on the progress bar & for skip buttons.
 */
@JsonClass(generateAdapter = true)
data class RangeLong(
    val startMs: Long,
    val endMs: Long
)

/**
 * Resolved stream data used by the media engine.
 */
@JsonClass(generateAdapter = true)
data class StreamData(
    val url: String,
    val type: StreamType,
    val headers: Map<String, String> = emptyMap(),
    // True if [url] holds the raw media content itself — e.g. an HLS playlist text
    // starting with "#EXTM3U...". False if [url] is a link that still needs to be
    // resolved (fetched) before playback.
    val isContent: Boolean = false,
    val subtitles: List<SubtitleInfo> = emptyList(),
    val intro: RangeLong? = null,
    val outro: RangeLong? = null
)

@JsonClass(generateAdapter = true)
data class SubtitleInfo(
    val url: String,
    val language: String,
    val label: String? = null,
    // Extra headers for fetching this subtitle. Note: MediaItem.SubtitleConfiguration
    // (media3 1.11) carries no per-subtitle header API, so subtitle requests ride the
    // shared data source factory which already applies [StreamData.headers]; kept for
    // sources that resolve subtitles through their own channel.
    val headers: Map<String, String> = emptyMap()
)

enum class StreamType {
    HLS, MP4, DASH, OTHER
}
