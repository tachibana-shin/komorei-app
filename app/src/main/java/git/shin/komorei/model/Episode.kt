package git.shin.komorei.model

/**
 * Represents a single episode of an anime.
 */
data class Episode(
    val id: String,
    val animeId: String,
    val sourceId: String,
    val episodeNumber: String,
    val title: String,
    val videoUrl: String,
    val durationSeconds: Long = 1440L, // e.g. 24 minutes standard
    val thumbnailUrl: String = "",
    val quality: String = "1080p FHD"
)
