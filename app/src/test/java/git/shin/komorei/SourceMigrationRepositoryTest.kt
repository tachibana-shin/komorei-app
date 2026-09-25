package git.shin.komorei

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceMigrationRepository
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.data.local.dao.AnimeDao
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * MigrationHandler plumbing: the runner's `migrate_anime` / `migrate_episode`
 * exports round-trip through the REAL `fake-vi-source.krx` (identity source),
 * and [SourceMigrationRepository] re-keys the Room library + watch history via
 * the injectable remapper seams — proving the rewrite (and collision merge)
 * logic without depending on a source that actually changes its ids.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SourceMigrationRepositoryTest {
    private lateinit var database: KomoreiDatabase
    private lateinit var dao: AnimeDao
    private lateinit var repository: AnimeRepository

    companion object {
        private const val SOURCE_ID = "vi.fake-source"
        private val fakeKrx: String =
            System.getProperty("komorei.test.fakeKrx")
                ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() =
        runBlocking {
            assertNotNull(
                "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
                System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
            )
            val context = ApplicationProvider.getApplicationContext<Context>()
            database =
                Room
                    .inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            dao = database.animeDao()
            val registry = KrxSourceRegistry(context, KrxHostImpl(context))
            val runner = registry.loadKrx(SOURCE_ID, File(fakeKrx).readBytes())
            assertNotNull("fake source should load", runner)
            repository = AnimeRepository(registry)
        }

    @After
    fun tearDown() {
        database.close()
    }

    private fun anime(id: String) =
        Anime(
            id = id,
            sourceId = SOURCE_ID,
            title = "Anime $id",
            originalTitle = "",
            posterUrl = "",
            bannerUrl = "",
            description = "",
            episodeCount = 2,
            currentEpisode = null,
            rating = null,
            ratingCount = null,
            status = AnimeStatus.UNKNOWN,
            releaseYear = null,
            genres = emptyList(),
            authors = emptyList(),
            studio = null,
            seasonOf = null,
        )

    private fun history(
        animeId: String,
        episodeId: String,
    ) = WatchHistoryEntity(
        animeId = animeId,
        sourceId = SOURCE_ID,
        episodeId = episodeId,
        episodeNumber = "1",
        episodeTitle = "Tập 1",
        lastWatchedAt = 1_000L,
        progressMs = 60_000L,
        durationMs = 120_000L,
    )

    private fun newService() = SourceMigrationRepository(dao, repository)

    // ── REAL runner round-trips (identity source) ─────────────────────────

    @Test
    fun runnerMapsAnimeKeyThroughWasm() =
        runBlocking {
            assertEquals(
                "frieren_journey",
                repository.migrateAnime(SOURCE_ID, "frieren_journey"),
            )
        }

    @Test
    fun runnerMapsEpisodeKeyThroughWasm() =
        runBlocking {
            assertEquals(
                "frieren_journey_ep_2",
                repository.migrateEpisode(SOURCE_ID, "frieren_journey", "frieren_journey_ep_2"),
            )
        }

    // ── identity passes touch nothing ──────────────────────────────────────

    @Test
    fun identityMigrationKeepsRowsAndReportsExaminedCounts() =
        runBlocking {
            dao.upsertAnime(
                AnimeEntity(anime("frieren_journey"), isBookmarked = true, bookmarkAddedAt = 5L),
            )
            dao.upsertWatchHistory(history("frieren_journey", "frieren_journey_ep_1"))

            val report = newService().migrateLibrary(SOURCE_ID)

            assertEquals(1, report.examinedAnimes)
            assertEquals(1, report.examinedEpisodes)
            assertEquals(0, report.migratedAnimes)
            assertEquals(0, report.migratedEpisodes)
            assertEquals(
                listOf("frieren_journey"),
                dao.getAnimesForSource(SOURCE_ID).map { it.anime.id },
            )
            assertEquals(
                listOf("frieren_journey_ep_1"),
                dao.getWatchHistoryForSource(SOURCE_ID).map { it.episodeId },
            )
        }

    // ── remapped keys rewrite both tables ──────────────────────────────────

    @Test
    fun changedAnimeKeyRewritesBookmarksAndHistory() =
        runBlocking {
            dao.upsertAnime(AnimeEntity(anime("old_anime"), isBookmarked = true, bookmarkAddedAt = 5L))
            dao.upsertWatchHistory(history("old_anime", "old_ep_1"))
            dao.upsertWatchHistory(history("old_anime", "old_ep_2"))
            // An unrelated anime is left untouched.
            dao.upsertAnime(AnimeEntity(anime("stable"), isBookmarked = true, bookmarkAddedAt = 6L))
            dao.upsertWatchHistory(history("stable", "stable_ep_1"))

            val service = newService()
            service.migrateAnimeKey = { _, key -> if (key == "old_anime") "new_anime" else key }
            service.migrateEpisodeKey = { _, animeKey, ep ->
                if (animeKey == "old_anime") ep.replace("old_ep", "new_ep") else ep
            }
            val report = service.migrateLibrary(SOURCE_ID)

            assertEquals(2, report.examinedAnimes)
            assertEquals(3, report.examinedEpisodes)
            assertEquals(1, report.migratedAnimes)
            assertEquals(2, report.migratedEpisodes)

            assertEquals(
                setOf("new_anime", "stable"),
                dao.getAnimesForSource(SOURCE_ID).map { it.anime.id }.toSet(),
            )
            assertEquals(
                setOf(
                    "new_anime" to "new_ep_1",
                    "new_anime" to "new_ep_2",
                    "stable" to "stable_ep_1",
                ),
                dao.getWatchHistoryForSource(SOURCE_ID).map { it.animeId to it.episodeId }.toSet(),
            )
        }

    @Test
    fun changedEpisodeKeyRewritesHistoryOnly() =
        runBlocking {
            dao.upsertAnime(AnimeEntity(anime("stable"), isBookmarked = true, bookmarkAddedAt = 6L))
            dao.upsertWatchHistory(history("stable", "old_ep_5"))

            val service = newService()
            service.migrateAnimeKey = { _, key -> key }
            service.migrateEpisodeKey = { _, _, _ -> "new_ep_5" }
            val report = service.migrateLibrary(SOURCE_ID)

            assertEquals(1, report.migratedEpisodes)
            assertEquals(listOf("new_ep_5"), dao.getWatchHistoryForSource(SOURCE_ID).map { it.episodeId })
            // The anime table keeps its id — only the episode moved.
            assertEquals(listOf("stable"), dao.getAnimesForSource(SOURCE_ID).map { it.anime.id })
        }

    // ── collision safety ───────────────────────────────────────────────────

    @Test
    fun twoOldKeysMappingToOneNewKeyKeepBothEpisodeRows() =
        runBlocking {
            dao.upsertWatchHistory(history("frieren", "frieren_ep_1"))
            dao.upsertWatchHistory(history("frieren_journey", "frieren_journey_ep_1"))

            val service = newService()
            service.migrateAnimeKey = { _, key -> if (key == "frieren") "frieren_journey" else key }
            service.migrateEpisodeKey = { _, _, ep -> ep }
            val report = service.migrateLibrary(SOURCE_ID)

            assertEquals(1, report.migratedAnimes)
            assertEquals(1, report.migratedEpisodes)
            assertEquals(
                setOf("frieren_ep_1", "frieren_journey_ep_1"),
                dao.getWatchHistoryForSource(SOURCE_ID).map { it.episodeId }.toSet(),
            )
            assertEquals(
                listOf("frieren_journey"),
                dao.getWatchHistoryForSource(SOURCE_ID).map { it.animeId }.distinct(),
            )
        }

    // ── a failed source load must not corrupt the library ─────────────────

    @Test
    fun nullRemapIsTreatedAsIdentity() =
        runBlocking {
            dao.upsertWatchHistory(history("frieren_journey", "frieren_journey_ep_1"))

            val service = newService()
            service.migrateAnimeKey = { _, _ -> null }
            service.migrateEpisodeKey = { _, _, _ -> null }
            val report = service.migrateLibrary(SOURCE_ID)

            assertEquals(0, report.migratedAnimes)
            assertEquals(0, report.migratedEpisodes)
            val rows = dao.getWatchHistoryForSource(SOURCE_ID)
            assertEquals(1, rows.size)
            assertEquals("frieren_journey", rows[0].animeId)
            assertEquals("frieren_journey_ep_1", rows[0].episodeId)
        }
}
