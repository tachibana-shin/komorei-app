package git.shin.komorei.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "watch_history",
    primaryKeys = ["animeId", "sourceId", "episodeId"]
)
data class WatchHistoryEntity(
    val animeId: String,
    val sourceId: String,
    val episodeId: String,
    val episodeNumber: String,
    val episodeTitle: String,
    val lastWatchedAt: Long,
    val progressMs: Long,
    val durationMs: Long
)
