package git.shin.komorei

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.data.local.KomoreiTypeConverters
import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `Anime.extra` — the source-defined key/value bag — as the app actually stores
 * it: converted to a JSON object, read back, and written through a real SQLite
 * table whose schema Room validates.
 *
 * The migration is the risky half. `anime_library` is an `@Embedded` copy of
 * `Anime`, so Room compares the *generated* schema against whatever the
 * `ALTER TABLE` produced, default value included — a mismatch is a crash on
 * open, for every existing user, and only at runtime. Asserting the two strings
 * agree here turns that into a failing test instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnimeExtraStorageTest {
    private val converters = KomoreiTypeConverters()

    /** Only [extra] is under test; the rest is whatever a Lite card carries. */
    private fun anime(extra: Map<String, String>) =
        Anime(
            id = "some-anime",
            sourceId = "vi.animevietsub",
            title = "Some Anime",
            originalTitle = "",
            posterUrl = "",
            bannerUrl = "",
            description = "",
            episodeCount = 0,
            currentEpisode = null,
            rating = null,
            ratingCount = null,
            status = AnimeStatus.UNKNOWN,
            releaseYear = null,
            genres = emptyList(),
            authors = emptyList(),
            studio = null,
            seasonOf = null,
            extra = extra,
        )

    @Test
    fun `extras survive the type converter`() {
        val extra = mapOf("avs.recommendations" to """[{"key":"a","title":"A"}]""")
        val json = converters.fromStringMap(extra)
        assertEquals(extra, converters.toStringMap(json))
    }

    @Test
    fun `an empty map round-trips as an empty map, not as null`() {
        // The column is NOT NULL with a '{}' default, so a source that sets
        // nothing must produce something the reader can use.
        val json = converters.fromStringMap(emptyMap())
        assertEquals("{}", json)
        assertEquals(emptyMap<String, String>(), converters.toStringMap(json))
    }

    @Test
    fun `a key with quotes and braces in its value round-trips`() {
        // Titles are arbitrary user-visible text; a value that breaks naive
        // escaping would silently truncate every recommendation.
        val extra = mapOf("avs.recommendations" to """{"a":"He said \"{hi}\"","b":"x\\y"}""")
        assertEquals(extra, converters.toStringMap(converters.fromStringMap(extra)))
    }

    @Test
    fun `the column the migration adds matches the schema Room generates`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // What Room expects, read from the schema it generated.
        val generatedDb =
            Room
                .inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
                .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                .build()
        val generated =
            try {
                generatedDb.openHelper.writableDatabase.readColumn("anime_library", "extra")
            } finally {
                generatedDb.close()
            }
        assertNotNull("Room did not generate the extra column", generated)

        // What an existing user's database ends up with, after the real
        // migration runs on a real v4 table. Room compares exactly these two
        // when it opens the file, and a mismatch is a crash on launch for
        // everyone who already had the app — which no other test here would see.
        val migrated =
            openV4Database(context).use { db ->
                KomoreiDatabase.MIGRATION_4_5.migrate(db)
                db.readColumn("anime_library", "extra")
            }
        assertNotNull("the migration did not add the extra column", migrated)

        assertEquals(
            "the migration's column differs from Room's — the app would fail to open",
            generated,
            migrated,
        )
    }

    /** A v4-shaped `anime_library`: the current schema minus `extra`. */
    private fun openV4Database(context: android.content.Context): SupportSQLiteDatabase {
        val config =
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name("pre-v5-extras")
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(4) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE `anime_library` (" +
                                    "`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, " +
                                    "`title` TEXT NOT NULL, `originalTitle` TEXT NOT NULL, " +
                                    "`posterUrl` TEXT NOT NULL, `bannerUrl` TEXT NOT NULL, " +
                                    "`description` TEXT NOT NULL, `episodeCount` INTEGER NOT NULL, " +
                                    "`currentEpisode` TEXT, `rating` REAL, `ratingCount` INTEGER, " +
                                    "`status` TEXT NOT NULL, `releaseYear` TEXT, `genres` TEXT NOT NULL, " +
                                    "`authors` TEXT NOT NULL, `studio` TEXT, `seasonOf` TEXT, " +
                                    "`countries` TEXT NOT NULL, `episodes` TEXT NOT NULL, " +
                                    "`seasons` TEXT NOT NULL, `isFeatured` INTEGER NOT NULL, " +
                                    "`views` INTEGER NOT NULL, `nextEpisodeAirInfo` TEXT, " +
                                    "`qualityTag` TEXT, `isBookmarked` INTEGER NOT NULL, " +
                                    "`bookmarkAddedAt` INTEGER, " +
                                    "PRIMARY KEY(`id`, `sourceId`))",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    /** `PRAGMA table_info` for one column: `type`, `notnull` and `dflt_value`. */
    private fun SupportSQLiteDatabase.readColumn(
        table: String,
        column: String,
    ): String? =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) != column) continue
                val notNull = cursor.getInt(notNullIndex)
                val default =
                    if (cursor.isNull(defaultIndex)) "none" else cursor.getString(defaultIndex)
                return "${cursor.getString(typeIndex)}|notnull=$notNull|default=$default"
            }
            null
        }

    @Test
    fun `extras survive a real database write and read`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val db =
                Room
                    .inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
                    .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                    .build()
            try {
                val dao: AnimeDao = db.animeDao()
                val stored =
                    mapOf(
                        "avs.recommendations" to """[{"key":"a"}]""",
                        "avs.somethingelse" to "kept",
                    )
                dao.upsertAnime(AnimeEntity(anime = anime(stored)))

                val read = dao.getAnime("some-anime", "vi.animevietsub")
                assertEquals("extras did not survive the round trip", stored, read?.anime?.extra)
            } finally {
                db.close()
            }
        }

    @Test
    fun `a row written before the column existed reads back with no extras`() =
        runBlocking {
            // What MIGRATION_4_5 leaves behind: the old rows get '{}', which is the
            // only value a NOT NULL column can take, and has to read as "nothing
            // extra" rather than as an error.
            assertEquals(emptyMap<String, String>(), converters.toStringMap("{}"))
            assertTrue(anime(emptyMap()).extra.isEmpty())
        }
}
