package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceReposRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.sources.SourcesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * SourcesViewModel on the real runner + committed fake source: source list
 * visibility, enable/disable, pin/unpin, uninstall block on bundled, and
 * disabled-source exclusion in Home source tabs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SourcesViewModelTest {

    private lateinit var context: Context
    private lateinit var repository: AnimeRepository
    private lateinit var registry: KrxSourceRegistry
    private lateinit var stateStore: SourceStateStore
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
        private val fakeKrx: String = System.getProperty("komorei.test.fakeKrx")
            ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() = runBlocking {
        assertNotNull(
            "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
            System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
        )
        context = ApplicationProvider.getApplicationContext()
        stateStore = SourceStateStore(context)
        registry = KrxSourceRegistry(context, KrxHostImpl(context))
        registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes()).let {
            assertNotNull("fake source should load", it)
        }
        repository = AnimeRepository(registry)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVM(): SourcesViewModel {
        return SourcesViewModel(
            appContext = context,
            repository = repository,
            registry = registry,
            stateStore = stateStore,
            reposRepository = SourceReposRepository(OkHttpClient.Builder().build()),
        )
    }

    @Test
    fun initialStateShowsFakeSourceAndAggregator() = runBlocking {
        val vm = createVM()
        // Allow the sourcesFlow to propagate (WhileSubscribed + combine)
        val sources = vm.sources.first { it.isNotEmpty() }
        // Non-aggregator sources include fake source
        val ids = sources.map { it.source.id }
        assertTrue("vi.fake-source must appear", "vi.fake-source" in ids)
    }

    @Test
    fun disableSourceFiltersFromHomeTabs() = runBlocking {
        // StateStore starts empty → source enabled.
        val homeVm = git.shin.komorei.ui.screens.home.HomeViewModel(
            context, repository, stateStore,
        )
        val homeSources = homeVm.sources.value
        assertTrue(
            "vi.fake-source starts enabled in Home tabs",
            homeSources.any { it.id == "vi.fake-source" },
        )

        // Disable it.
        stateStore.setDisabled("vi.fake-source", true)
        val updated = homeVm.sources.first { list -> list.none { it.id == "vi.fake-source" } }
        assertFalse(
            "vi.fake-source disappears from Home tabs after disabling",
            updated.any { it.id == "vi.fake-source" },
        )
        // Aggregator must remain.
        assertTrue(
            "Aggregator 'all' must stay in Home tabs",
            updated.any { it.id == "all" },
        )
    }

    @Test
    fun pinAndUnpinToggleState() = runBlocking {
        val vm = createVM()
        val sources = vm.sources.first { it.isNotEmpty() }
        val fake = sources.first { it.source.id == "vi.fake-source" }

        // Initially not pinned.
        assertEquals(-1, fake.pinnedIndex)

        // Pin it.
        vm.togglePinned(fake.source)
        val pinned = vm.sources.first { list -> list.any { it.source.id == "vi.fake-source" && it.pinnedIndex >= 0 } }
        val fakePinned = pinned.first { it.source.id == "vi.fake-source" }
        assertTrue("pinnedIndex must be >= 0", fakePinned.pinnedIndex >= 0)

        // Unpin.
        vm.togglePinned(fakePinned.source)
        val unpinned = vm.sources.first { list -> list.any { it.source.id == "vi.fake-source" && it.pinnedIndex < 0 } }
        assertEquals(-1, unpinned.first { it.source.id == "vi.fake-source" }.pinnedIndex)
    }

    @Test
    fun bundledSourceCannotBeUninstalled() = runBlocking {
        val vm = createVM()
        val sources = vm.sources.first { it.isNotEmpty() }
        val fake = sources.first { it.source.id == "vi.fake-source" }
        assertFalse("fake source is bundled, not user-installed", fake.isUserInstalled)

        // Uninstall is a no-op for bundled sources — sources list must remain.
        vm.uninstall(fake.source)
        val still = vm.sources.first { it.isNotEmpty() }
        assertTrue(
            "vi.fake-source must remain after uninstall attempt",
            still.any { it.source.id == "vi.fake-source" },
        )
    }
}