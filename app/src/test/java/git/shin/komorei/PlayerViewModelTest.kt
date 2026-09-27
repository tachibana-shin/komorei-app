package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.Episode
import git.shin.komorei.model.StreamData
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
 * Player resolution regression tests for [PlayerViewModel]:
 *
 * Opening an anime directly (card tap / banner / search result) passes a
 * Lite card whose `episodes` is empty, so [PlayerViewModel.openAnime] builds a
 * placeholder episode (`<animeId>_ep_1`). `loadStreams` must then swap in the
 * FIRST REAL episode from the upgraded anime instead of sending the phantom key
 * to the source — otherwise KKPhim-style sources bail with
 * "Nguồn phát X không có tập 1 (<animeId>_ep_1)" and no episode is highlighted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class PlayerViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var registry: KrxSourceRegistry
    private lateinit var repository: AnimeRepository
    private lateinit var libraryRepository: LibraryRepository

    companion object {
        private const val FAKE_SOURCE = "vi.fake-source"
        private const val CATALOG_KEY = "solo_leveling_s2"

        private val fakeKrx: String =
            System.getProperty("komorei.test.fakeKrx")
                ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() =
        runBlocking {
            // Before anything that can reach `Dispatchers.Main`: the test
            // dispatcher has to be installed first, or whichever test class
            // happens to run first in a fresh JVM fails on it.
            Dispatchers.setMain(mainDispatcher)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val host = KrxHostImpl(context)
            registry = KrxSourceRegistry(context, host)
            registry.loadKrx(FAKE_SOURCE, File(fakeKrx).readBytes())
            repository = AnimeRepository(registry)
            val db = Room.inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java).build()
            libraryRepository = LibraryRepository(db.animeDao())
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newPlayerViewModel(): PlayerViewModel =
        PlayerViewModel(
            ApplicationProvider.getApplicationContext(),
            repository,
            libraryRepository,
            SavedStateHandle(),
        )

    /** A valid Lite card (id + sourceId only) like the ones home/list/search return. */
    private fun liteAnime(key: String): Anime =
        Anime(
            id = key,
            sourceId = FAKE_SOURCE,
            title = key,
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
        )

    /**
     * Waits for the openAnime stream pipeline to resolve. The runner round-trips
     * hop through a real per-source wasm thread ([KrxSourceRegistry] dispatches
     * each call onto its own executor), so the test scheduler alone cannot drive
     * them: drain queued Main continuations, then give the IO threads a moment to
     * enqueue the next one. The 250ms position poller would keep the scheduler
     * from ever idling, so it is cancelled first.
     *
     * Main here MUST be a StandardTestDispatcher (not an Unconfined one): the
     * engine's ExoPlayer verifies the application thread, and an unconfined
     * resume runs the continuation inline on the thread that completed the wasm
     * call — every player access from loadStreams then throws
     * "Player is accessed on the wrong thread". Standard queues the resume, so
     * all post-withContext code runs on the test (Robolectric main) thread.
     */
    private fun TestScope.awaitStreamResolution(vm: PlayerViewModel) {
        vm.positionPoller?.cancel()
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.playbackState.value.streamData == null &&
            vm.playbackState.value.streamError == null &&
            System.currentTimeMillis() < deadline
        ) {
            advanceUntilIdle()
            Thread.sleep(20)
        }
        advanceUntilIdle()
    }

    @Test
    fun openingAnAnimeMarksTheStreamLoadStartedInTheSameUpdate() =
        runTest(mainDispatcher) {
            // `openAnime` makes the sheet visible in the same state update. The
            // detail screen reads `isLoadingStreams` to tell "the player is still
            // fetching" from "the player settled and has no record for me", and
            // takes the second reading as a cue to fetch the anime itself — so
            // the flag has to be true in the same update that reveals the sheet.
            //
            // Left at its previous value it is `false`, the sheet composes with
            // that, and the detail screen goes and asks the source for the same
            // anime a second time. Sources that mint a fresh handle per page load
            // (AnimeVietsub's `data-id`/`data-hash`) then hand back *different*
            // episode keys and the episode strip highlights nothing. This is the
            // third distinct cause of that same symptom, all in the same area.
            val vm = newPlayerViewModel()
            vm.openAnime(liteAnime(CATALOG_KEY))

            assertTrue(
                "the sheet is already visible, so the stream load must already read as started",
                vm.playbackState.value.isLoadingStreams,
            )
            awaitStreamResolution(vm)
        }

    @Test
    fun theUpgradedAnimeSurvivesAFailedStreamResolve() =
        runTest(mainDispatcher) {
            // The detail screen reuses `playbackState.fullAnime` instead of
            // fetching the anime a second time, and that reuse is what keeps the
            // episode strip's selection working: some sources mint a fresh
            // per-page-load handle per episode (AnimeVietsub's `data-id`/
            // `data-hash` are the signed pair `POST /ajax/player` wants, and they
            // change between loads), so a second fetch returns *different* episode
            // keys and nothing can match `currentEpisode`.
            //
            // It only holds if the upgrade is published before the stream resolve,
            // because a failed resolve is exactly the case the detail screen has
            // to cover. Measured on a real AnimeVietsub title: player saw
            // 24 episodes keyed `1-112705-vd02_…`, the detail screen's own fetch
            // saw 23 keyed `1-69386-Sr9V…` — nothing highlighted.
            val lite = liteAnime(CATALOG_KEY)
            val expected = repository.getAnimeUpdate(lite, needsDetails = true, needsChapters = true).episodes

            // Wraps the SAME registry rather than building a second one: a second
            // `loadKrx` in the same JVM re-enters JNA, whose native side is
            // initialised once per classloader.
            val failing =
                object : AnimeRepository(registry) {
                    // A source whose playlist refuses to resolve: the "playlist is
                    // not valid base64" failure that leaves the player on its
                    // error state with the detail sheet still up.
                    override suspend fun getStream(
                        anime: Anime,
                        episode: Episode,
                        stream: StreamInfo,
                    ): StreamData = throw IllegalStateException("Không giải được playlist: base64 không hợp lệ")
                }
            val failingVm =
                PlayerViewModel(
                    ApplicationProvider.getApplicationContext(),
                    failing,
                    libraryRepository,
                    SavedStateHandle(),
                )

            failingVm.openAnime(lite)
            awaitStreamResolution(failingVm)

            val state = failingVm.playbackState.value
            assertNotNull("the resolve must actually have failed for this to test anything", state.streamError)
            assertNotNull(
                "the upgrade must be published even when the stream resolve fails — " +
                    "otherwise the detail screen refetches and gets different episode keys",
                state.fullAnime,
            )
            assertEquals(
                "the published record must be the one whose keys `currentEpisode` uses",
                expected.map { it.id },
                state.fullAnime?.episodes?.map { it.id },
            )
            assertEquals(
                state.currentEpisode?.id,
                state.fullAnime
                    ?.episodes
                    ?.firstOrNull()
                    ?.id,
            )
        }

    @Test
    fun openAnimeWithoutEpisodeResolvesFirstRealEpisodeAndStream() =
        runTest(mainDispatcher) {
            val vm = newPlayerViewModel()
            val lite = liteAnime(CATALOG_KEY)
            val firstReal =
                repository
                    .getAnimeUpdate(lite, needsDetails = true, needsChapters = true)
                    .episodes
                    .first()
            assertEquals("${CATALOG_KEY}_ep_1", firstReal.id)

            vm.openAnime(lite)
            awaitStreamResolution(vm)

            val state = vm.playbackState.value
            assertEquals(firstReal.id, state.currentEpisode?.id)
            assertEquals(firstReal.title, state.currentEpisode?.title)
            assertNull(state.streamError)
            assertNotNull(state.streamData)
        }

    @Test
    fun openAnimeWithUnmatchedEpisodeFallsBackToFirstRealOne() =
        runTest(mainDispatcher) {
            val vm = newPlayerViewModel()
            val lite = liteAnime(CATALOG_KEY)
            val firstReal =
                repository
                    .getAnimeUpdate(lite, needsDetails = true, needsChapters = true)
                    .episodes
                    .first()
            // A key the source's episode list never contains (a stale deep link, or the
            // "<animeId>_ep_1" placeholder openAnime builds when a Lite card arrives
            // with no episodes) — must NOT be sent to get_stream.
            val ghost =
                Episode(
                    id = "${CATALOG_KEY}__trailer",
                    animeId = lite.id,
                    sourceId = lite.sourceId,
                    episodeNumber = "TRAILER",
                    title = "Trailer",
                )

            vm.openAnime(lite, ghost)
            awaitStreamResolution(vm)

            val state = vm.playbackState.value
            assertEquals(firstReal.id, state.currentEpisode?.id)
            assertEquals(firstReal.title, state.currentEpisode?.title)
            assertNull(state.streamError)
            assertNotNull(state.streamData)
        }
}
