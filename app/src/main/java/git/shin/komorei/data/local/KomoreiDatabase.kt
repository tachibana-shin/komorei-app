package git.shin.komorei.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue

@Database(
    entities = [
        AnimeEntity::class,
        WatchHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(KomoreiTypeConverters::class)
abstract class KomoreiDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
}
