package git.shin.komorei

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.SearchHistoryStore
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.backup.BackupCodec
import git.shin.komorei.data.backup.BackupRepository
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.data.local.entity.AnimeEntity
import git.shin.komorei.data.local.entity.KrxDefaultsEntity
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(sdk = [36])
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: KomoreiDatabase
    private lateinit var sourceStateStore: SourceStateStore
    private lateinit var searchHistoryStore: SearchHistoryStore
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "backups").deleteRecursively()
        context
            .getSharedPreferences("source_manager", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences("search_history", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        database =
            Room
                .inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val host = KrxHostImpl(context)
        val registry = KrxSourceRegistry(context, host)
        sourceStateStore = SourceStateStore(context)
        searchHistoryStore = SearchHistoryStore(context)
        repository =
            BackupRepository(
                context = context,
                database = database,
                animeDao = database.animeDao(),
                defaultsDao = database.krxDefaultsDao(),
                sourceStateStore = sourceStateStore,
                searchHistoryStore = searchHistoryStore,
                sourceRegistry = registry,
                codec = BackupCodec(),
            )
    }

    @After
    fun tearDown() {
        File(context.filesDir, "backups").deleteRecursively()
        database.close()
    }

    @Test
    fun createsReadableBackupAndRestoresOptionalSections() =
        runBlocking {
            val anime = testAnime()
            database.animeDao().upsertAnime(AnimeEntity(anime, isBookmarked = true, bookmarkAddedAt = 10L))
            database.animeDao().upsertWatchHistory(
                WatchHistoryEntity("anime", "source", "episode", "1", "Ep 1", 20L, 30_000L, 120_000L),
            )
            database.krxDefaultsDao().upsert(KrxDefaultsEntity("source.language", "string", "vi"))
            sourceStateStore.setDisabled("old", true)
            sourceStateStore.togglePinned("source")
            sourceStateStore.addRepo("https://example.test/repo.json")
            searchHistoryStore.addQuery("one")

            val info =
                repository.createLocalBackup(
                    name = "round trip",
                    options =
                        git.shin.komorei.data.backup.BackupOptions(
                            includeSourceDefaults = true,
                            includeSearchHistory = true,
                        ),
                )
            assertTrue(info.valid)
            assertEquals(1, info.counts.library)
            assertEquals(1, info.counts.history)
            assertEquals(1, info.counts.sourceDefaults)

            database.animeDao().deleteAllAnimeEntities()
            database.animeDao().deleteAllWatchHistory()
            database.krxDefaultsDao().deleteAll()
            sourceStateStore.setDisabled("source", true)
            sourceStateStore.togglePinned("source")
            sourceStateStore.removeRepo("https://example.test/repo.json")
            searchHistoryStore.clearHistory()

            val result = repository.restore(info.fileName)

            assertEquals(1, result.restored.library)
            assertEquals(1, result.restored.history)
            assertEquals(
                "anime",
                database
                    .animeDao()
                    .getAllAnimeEntities()
                    .single()
                    .anime.id,
            )
            assertEquals(
                "source.language",
                database
                    .krxDefaultsDao()
                    .getAll()
                    .single()
                    .key,
            )
            val restoredSourceState = SourceStateStore(context)
            assertTrue(restoredSourceState.isDisabled("old"))
            assertEquals(listOf("source"), restoredSourceState.pinned.value)
            assertEquals(listOf("https://example.test/repo.json"), restoredSourceState.repos.value)
            assertEquals(listOf("one"), SearchHistoryStore(context).snapshot())
        }

    private fun testAnime() =
        Anime(
            id = "anime",
            sourceId = "source",
            title = "Anime",
            originalTitle = "Anime",
            posterUrl = "",
            bannerUrl = "",
            description = "",
            episodeCount = 1,
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
}
