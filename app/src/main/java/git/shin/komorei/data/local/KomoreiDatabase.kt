package git.shin.komorei.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity

@Database(
    entities = [
        AnimeEntity::class,
        WatchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(KomoreiTypeConverters::class)
abstract class KomoreiDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
}
