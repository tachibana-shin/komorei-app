package git.shin.komorei.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StreamInfo(
    val id: String,
    val name: String,
    val quality: String
)

/**
 * Resolved stream data used by the media engine.
 */
@JsonClass(generateAdapter = true)
data class StreamData(
    val url: String,
    val type: StreamType,
    val headers: Map<String, String> = emptyMap(),
    val isContent: Boolean = true, // True if direct video link, false if requires resolution
    val subtitles: List<SubtitleInfo> = emptyList(),
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val outroStartMs: Long? = null,
    val outroEndMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class SubtitleInfo(
    val url: String,
    val language: String,
    val label: String? = null
)

enum class StreamType {
    HLS, MP4, DASH, OTHER
}
