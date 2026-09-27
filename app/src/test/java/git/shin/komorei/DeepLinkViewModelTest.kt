package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.deeplink.DeepLinkManager
import git.shin.komorei.data.deeplink.DeepLinkResolver
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.deeplink.DeepLinkAction
import git.shin.komorei.ui.deeplink.DeepLinkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
 * The consumption half of DeepLinkHandler: [MainActivity] submits the intent
 * URI into [DeepLinkManager], [DeepLinkViewModel] resolves it via the REAL
 * runner + `fake-vi-source.krx` and exposes a one-shot [DeepLinkAction] that
 * [MainScreen] performs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeepLinkViewModelTest {
    private lateinit var repository: AnimeRepository
    private lateinit var manager: DeepLinkManager
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
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
            assertNotNull(
                "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
                System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
            )
            val context = ApplicationProvider.getApplicationContext<Context>()
            val registry = KrxSourceRegistry(context, KrxHostImpl(context))
            val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
            assertNotNull("fake source should load", runner)
            repository = AnimeRepository(registry)
            manager = DeepLinkManager()
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): DeepLinkViewModel = DeepLinkViewModel(manager, DeepLinkResolver(repository))

    private suspend fun awaitAction(vm: DeepLinkViewModel): DeepLinkAction =
        withTimeout(15_000) {
            while (vm.action.value == null) delay(50)
            vm.action.value!!
        }

    @Test
    fun animeLinkOpensPlayerWithLiteStub() =
        runBlocking {
            val vm = newViewModel()

            manager.submit("https://komorei.example/anime/frieren_journey")
            val action = awaitAction(vm) as DeepLinkAction.OpenAnime

            assertEquals("frieren_journey", action.anime.id)
            assertEquals("vi.fake-source", action.anime.sourceId)
            assertNull("anime links don't carry an episode", action.episode)
            // The manager is consumed once the action is produced, and the VM
            // exposes the action once so the UI performs it exactly once.
            assertNull(manager.pending.value)
        }

    @Test
    fun watchLinkOpensPlayerAtEpisode() =
        runBlocking {
            val vm = newViewModel()

            manager.submit("komorei://komorei.example/watch/frieren_journey/frieren_journey_ep_3")
            val action = awaitAction(vm) as DeepLinkAction.OpenAnime

            assertEquals("frieren_journey", action.anime.id)
            // The stub episode's id IS the runner key so stream resolution
            // serializes the right key (the full record replaces it after upgrade,
            // but the key must already match for that swap to happen).
            assertEquals("frieren_journey_ep_3", action.episode?.id)
            assertEquals("vi.fake-source", action.episode?.sourceId)
        }

    @Test
    fun listingLinkNavigatesToListing() =
        runBlocking {
            val vm = newViewModel()

            manager.submit("https://komorei.example/list/popular")
            val action = awaitAction(vm) as DeepLinkAction.OpenListing

            assertEquals("vi.fake-source", action.sourceId)
            assertEquals("popular", action.listing.id)
        }

    @Test
    fun unknownLinkProducesNoActionAndIsConsumed() =
        runBlocking {
            val vm = newViewModel()

            manager.submit("https://komorei.example/bogus-page")
            // The manager is consumed even when nothing resolves (dead link → the
            // app just ignores it, it never re-fires on recomposition).
            withTimeout(15_000) {
                while (manager.pending.value != null) delay(50)
            }
            assertNull(vm.action.value)
        }

    @Test
    fun markConsumedClearsTheAction() =
        runBlocking {
            val vm = newViewModel()

            manager.submit("https://komorei.example/anime/one_piece")
            val action = awaitAction(vm) as DeepLinkAction.OpenAnime
            assertEquals("one_piece", action.anime.id)

            vm.markConsumed()
            assertNull("performing the action must clear it", vm.action.value)
        }

    @Test
    fun secondSubmitOverridesPendingFirst() =
        runBlocking {
            val vm = newViewModel()

            manager.submit("https://komorei.example/anime/frieren_journey")
            manager.submit("https://komorei.example/anime/one_piece")
            val action = awaitAction(vm) as DeepLinkAction.OpenAnime

            assertTrue("last link wins", action.anime.id == "one_piece")
        }
}
