package git.shin.komorei.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import git.shin.komorei.model.Anime

@Entity(
    tableName = "anime_library",
    primaryKeys = ["id", "sourceId"]
)
data class AnimeEntity(
    @Embedded
    val anime: Anime,
    
    val isBookmarked: Boolean = false,
    val bookmarkAddedAt: Long? = null
)
