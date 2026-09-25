package git.shin.komorei.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.dao.KrxDefaultsDao
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.KrxDefaultsEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity

@Database(
    entities = [
        AnimeEntity::class,
        WatchHistoryEntity::class,
        KrxDefaultsEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(KomoreiTypeConverters::class)
abstract class KomoreiDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao

    abstract fun krxDefaultsDao(): KrxDefaultsDao

    companion object {
        /** v2 → v3: adds the `krx_defaults` table (Krx settings moved from
         *  SharedPreferences to Room). Preserves anime/watch-history data. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `krx_defaults` (" +
                            "`key` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`value` TEXT NOT NULL, " +
                            "PRIMARY KEY(`key`))",
                    )
                }
            }

        /** v3 → v4: `krx_defaults` keys are now source-namespaced
         *  (`{sourceId}.{key}`, Aidoku-style — see `krxDefaultsKey`). Rows
         *  written before namespacing used raw keys and cannot be attributed
         *  to a source (they were throwaway dev/test writes), so drop them.
         *  Anime/watch-history tables are untouched. */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DELETE FROM `krx_defaults`")
                }
            }
    }
}
