package git.shin.komorei.model

/**
 * Domain representation of an episode's watch progress.
 */
data class WatchHistory(
    val animeId: String,
    val sourceId: String,
    val episodeId: String,
    val progressMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
) {
    val progressFraction: Float
        get() = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f
}
