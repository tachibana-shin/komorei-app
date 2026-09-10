package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/**
 * Metadata for a streaming server or provider.
 */
@JsonClass(generateAdapter = true)
data class StreamInfo(
    val id: String,
    val name: String, // e.g. "Server VIP", "Mirror 1"
    val quality: String = "1080p"
)

/**
 * Resolved stream data used by the media engine.
 */
@JsonClass(generateAdapter = true)
data class StreamData(
    val url: String,
    val type: StreamType,
    val headers: Map<String, String> = emptyMap(),
    val isContent: Boolean = true // True if direct video link, false if requires resolution
)

/**
 * Supported streaming manifest or container types.
 */
enum class StreamType {
    HLS, MP4, DASH, OTHER
}
