package git.shin.komorei

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.CategoryLink
import git.shin.komorei.model.Episode
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.player.AnimeDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The detail screen's data loading, on the real runner + [KrxHostImpl] +
 * `vi.fake-source`, with [AnimeRepository.getAnimeUpdate] counted.
 *
 * Two things are being pinned here, and they are the same bug seen from two
 * sides.
 *
 * **The Lite record must never be what the screen shows.** The anime handed to
 * the detail screen comes from a listing API, so it carries a title and a poster
 * and nothing else. Rendering it while the upgrade is in flight shows a
 * real-looking header with no studio, no genres, no seasons and a zeroed rating
 * — indistinguishable from a source that returned broken data. The screen has to
 * say "loading" instead, which is what [AnimeDetailViewModel.isLoadingMetadata]
 * is for.
 *
 * **The upgrade must happen once, not three times.** The player already asks
 * for details *and* chapters in a single `getAnimeUpdate`; the detail screen
 * asking again for metadata and again for episodes meant three full source
 * fetches for one screen. On a source behind a JS challenge each of those is
 * seconds of skeleton, so the duplicate calls were the visible symptom.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnimeDetailViewModelTest {
    private lateinit var database: KomoreiDatabase
    private lateinit var repository: CountingRepository
    private lateinit var viewModel: AnimeDetailViewModel
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
        private val fakeKrx: String =
            System.getProperty("komorei.test.fakeKrx")
                ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    /**
     * Counts every upgrade so a test can assert on how many round trips the
     * source was actually asked for, and how they were parameterised.
     */
    private class CountingRepository(
        registry: KrxSourceRegistry,
    ) : AnimeRepository(registry) {
        val calls = mutableListOf<Pair<Boolean, Boolean>>()

        override suspend fun getAnimeUpdate(
            anime: Anime,
            needsDetails: Boolean,
            needsChapters: Boolean,
        ): Anime {
            calls += needsDetails to needsChapters
            return super.getAnimeUpdate(anime, needsDetails, needsChapters)
        }
    }

    @Before
    fun setUp() =
        runBlocking {
            // Before anything that can reach `Dispatchers.Main`: the test
            // dispatcher has to be installed first, or whichever test class
            // happens to run first in a fresh JVM fails on it.
            Dispatchers.setMain(mainDispatcher)
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
            val registry = KrxSourceRegistry(context, KrxHostImpl(context))
            assertNotNull("fake source should load", registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes()))
            repository = CountingRepository(registry)
            viewModel = AnimeDetailViewModel(context, repository, LibraryRepository(database.animeDao()))
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    /** The Lite record a listing would hand to the detail screen: title only. */
    private fun liteAnime() =
        Anime(
            id = "lite-1",
            sourceId = "vi.fake-source",
            title = "Lite Title Only",
            originalTitle = "",
            posterUrl = "https://example.test/poster.jpg",
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
        )

    /** The upgraded record the player's own `loadStreams` would have produced. */
    private fun fullAnime() =
        liteAnime().copy(
            description = "A real description that only the upgrade carries.",
            rating = 9.1f,
            ratingCount = 282,
            genres = listOf(CategoryLink("Action"), CategoryLink("Fantasy")),
            studio = CategoryLink("Bandai Namco Pictures"),
            episodes = listOf(Episode("e1", "lite-1", "vi.fake-source", "1", "Episode 1")),
            seasons = listOf(AnimeSeason("lite-1", "Season 1")),
        )

    @Test
    fun `no master record is exposed while the upgrade is in flight`() {
        val lite = liteAnime()
        viewModel.loadInitialData(lite)

        // Synchronously after the call: there must be no window in which the UI
        // could fall back to the Lite card it was given.
        val state = viewModel.uiState.value
        assertNull("the Lite record must not be the displayed record", state.masterAnime)
        assertTrue("the header must report loading", state.isLoadingMetadata)
    }

    @Test
    fun `a record that lost its id still selects the season that was opened`() =
        runBlocking {
            // AnimeVietsub's `parse_detail` builds a record without a key and the
            // SDK's `copy_from` assigns it unconditionally, so an upgraded anime
            // comes back with an EMPTY id — verified on device (`full.id=`).
            //
            // Matching the season list against that empty id finds nothing and
            // falls back to the first season, which on a multi-season title is not
            // the one the player opened. The detail screen then loads the wrong
            // season's episodes, none of whose keys match the playing episode, and
            // the episode strip highlights nothing. The screen has to trust the id
            // it was asked for, not the one the source handed back.
            val seasonTwo = AnimeSeason("other-season", "Season 2")
            val full =
                fullAnime().copy(
                    // Exactly what the source returns.
                    id = "",
                    episodes = listOf(Episode("s2e1", "other-season", "vi.fake-source", "1", "Episode 1")),
                    seasons = listOf(AnimeSeason("lite-1", "Season 1"), seasonTwo),
                )
            // The player was asked to open season 2, so that is the id this screen
            // knows. The upgraded record's own id is empty and must not be used.
            viewModel.loadInitialData(liteAnime().copy(id = "other-season"), full)

            val state = viewModel.uiState.value
            assertEquals(
                "the season matching the requested id must win, not the first listed",
                seasonTwo,
                state.selectedSeason,
            )
            assertEquals(
                "and its episodes must come off the record, not a second fetch",
                listOf("Episode 1"),
                state.currentSeasonEpisodes.map { it.title },
            )
        }

    @Test
    fun `a full anime from the player costs no upgrade at all`() =
        runBlocking {
            // The whole point. The player's `loadStreams` already fetched
            // details AND chapters, and that record carries the seasons and this
            // season's episodes — so the detail screen must be able to serve
            // metadata, the season list and the episode strip out of it without
            // asking the source for anything.
            viewModel.loadInitialData(liteAnime(), fullAnime())

            assertEquals(
                "the source must not be asked again for a record already in hand",
                0,
                repository.calls.size,
            )

            val state = viewModel.uiState.value
            assertTrue("the header must have its real record", !state.isLoadingMetadata)
            assertEquals("A real description that only the upgrade carries.", state.masterAnime?.description)
            assertEquals("Season 1", state.selectedSeason?.title)
            assertEquals(
                "the episodes come off the record the player already fetched",
                listOf("Episode 1"),
                state.currentSeasonEpisodes.map { it.title },
            )
        }

    @Test
    fun `a record without chapters still upgrades, once`() =
        runBlocking {
            // When the player could not supply chapters (a partial upgrade, or a
            // source that separates the two), the episode list genuinely is not
            // in hand — so exactly one more call, and no more.
            viewModel.loadInitialData(liteAnime(), fullAnime().copy(episodes = emptyList()))

            assertEquals(
                "only the missing chapters may be fetched",
                listOf(false to true),
                repository.calls.toList(),
            )
        }

    @Test
    fun `the previous anime is cleared before the next one is requested`() {
        runBlocking {
            // Re-entering the same anime is served from memory (the request guard
            // makes it a no-op), so this covers the *switch* case: stale state
            // from the previous anime must not survive into the new one.
            viewModel.loadInitialData(fullAnime().copy(id = "other", title = "Other"))
            val callsBefore = repository.calls.size
            assertTrue("sanity: the first anime should have been upgraded", callsBefore > 0)

            viewModel.loadInitialData(liteAnime())
            val state = viewModel.uiState.value
            assertNull("the previous anime must not linger", state.masterAnime)
            assertTrue(state.isLoadingMetadata)
        }
    }

    @Test
    fun `re-entering the same anime does not upgrade it again`() {
        runBlocking {
            viewModel.loadInitialData(liteAnime(), fullAnime())
            val callsAfterFirst = repository.calls.size

            // The player re-emits on every playback state change; the request
            // guard is what stops each of those from becoming another fetch.
            viewModel.loadInitialData(liteAnime(), fullAnime())
            viewModel.loadInitialData(liteAnime(), fullAnime())

            assertEquals(
                "re-entering must reuse the record, not refetch it",
                callsAfterFirst,
                repository.calls.size,
            )
        }
    }

    @Test
    fun `an upgrade failure stops reporting loading so the screen can offer a retry`() {
        runBlocking {
            // No master record and no error means a permanent skeleton. The retry
            // path needs a settled state to hang its button off.
            val failing =
                object : AnimeRepository(KrxSourceRegistry(context(), KrxHostImpl(context()))) {
                    override suspend fun getAnimeUpdate(
                        anime: Anime,
                        needsDetails: Boolean,
                        needsChapters: Boolean,
                    ): Anime = throw IllegalStateException("source is down")
                }
            val vm = AnimeDetailViewModel(context(), failing, LibraryRepository(database.animeDao()))
            vm.loadInitialData(liteAnime())

            val state = vm.uiState.value
            assertTrue("loading must end on failure too", !state.isLoadingMetadata)
            assertNull(state.masterAnime)
        }
    }

    @Test
    fun `a retry after a failure actually retries`() {
        runBlocking {
            // The request guard must not turn retry into a no-op, which is the
            // failure mode that leaves the user stuck on a permanent skeleton.
            var attempts = 0
            val flaky =
                object : AnimeRepository(KrxSourceRegistry(context(), KrxHostImpl(context()))) {
                    override suspend fun getAnimeUpdate(
                        anime: Anime,
                        needsDetails: Boolean,
                        needsChapters: Boolean,
                    ): Anime {
                        attempts++
                        if (attempts == 1) throw IllegalStateException("first attempt fails")
                        return fullAnime()
                    }
                }
            val vm = AnimeDetailViewModel(context(), flaky, LibraryRepository(database.animeDao()))
            vm.loadInitialData(liteAnime())
            assertEquals(1, attempts)

            vm.retryEpisodes()
            assertEquals("the retry must reach the source", 2, attempts)
            assertEquals(
                "Lite Title Only",
                vm.uiState.value.masterAnime
                    ?.title,
            )
        }
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()
}
