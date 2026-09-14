package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/**
 * Represents a single episode of an anime.
 * Note: videoUrl is NOT stored here as it's usually dynamic and fetched per session.
 */
@JsonClass(generateAdapter = true)
data class Episode(
    val id: String,
    val animeId: String,
    val sourceId: String,
    val episodeNumber: String,
    val title: String,
    val durationSeconds: Long? = null, // Reference only, actual duration comes from stream
    val thumbnailUrl: String = "",
    val quality: String = "1080p FHD",
    val dateUploaded: Long? = null, // epoch millis; mirrors the runner's date_uploaded (for "x ago" labels)
)
