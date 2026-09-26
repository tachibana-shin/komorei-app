package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.model.HomeComponent
import git.shin.komorei.model.HomeComponentValue
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.sdk.runner.HomePartialResult
import git.shin.komorei.ui.screens.home.HomeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import git.shin.komorei.sdk.runner.HomeComponent as RunnerHomeComponent
import git.shin.komorei.sdk.runner.HomeComponentValue as RunnerHomeComponentValue
import git.shin.komorei.sdk.runner.Link as RunnerLink

/**
 * How the Home tab paints a home that arrives in pieces.
 *
 * The point of streaming is that the reader sees the first rows while the source
 * is still fetching the rest, so the ordering is the whole feature: rows appear
 * during the load, and the layout `getHome` finally returns replaces them
 * without leaving duplicates, a flash of empty content, or rows from a load that
 * has already been superseded.
 *
 * Everything except the network is real here — a row is sent through the actual
 * `KrxHostImpl.partialHome` callback and travels the same `SharedFlow` the view
 * model collects in production. Only `getHome` is stubbed, because the states
 * worth pinning down are timing states and a real source cannot be made to be
 * slow on demand. The wasm half of the chain is covered by
 * `KrxRunnerIntegrationTest` and, in Rust, by
 * `home_streams_partial_results_before_it_returns`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeViewModelStreamingTest {
    private lateinit var context: Context
    private lateinit var host: KrxHostImpl
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        host = KrxHostImpl(context)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A row as a source sends it over the host callback. */
    private fun sent(title: String) =
        RunnerHomeComponent(
            title = title,
            subtitle = null,
            value =
                RunnerHomeComponentValue.Links(
                    listOf(RunnerLink(title = title, subtitle = null, imageUrl = null, value = null)),
                ),
        )

    /** A row as the app models it, i.e. what `getHome` returns. */
    private fun laid(title: String) = HomeComponent(title = title, subtitle = null, value = HomeComponentValue.Links(emptyList()))

    /**
     * A repository with the host's real stream and a `getHome` the test decides
     * on.
     *
     * [gate] is passed only by the tests that need to look at the screen while
     * the load is genuinely still running; without one, `getHome` returns
     * immediately so a test that only cares about the settled layout has nothing
     * to open.
     */
    private inner class FakeRepository(
        private val gate: CompletableDeferred<Unit>? = null,
    ) : AnimeRepository(KrxSourceRegistry(context, host)) {
        val fetchStarted = CompletableDeferred<Unit>()
        var returnedLayout: List<HomeComponent> = emptyList()

        override fun partialHomeResults(sourceId: String): Flow<HomeComponent> = host.partialHomeResults

        // The real one waits on the registry's own per-source host, a *different*
        // instance from the one this test emits into, so it would never see a
        // subscriber. In production the two always name the same host — which is
        // exactly the invariant this override stands in for.
        override suspend fun awaitPartialHomeSubscribers(
            sourceId: String,
            timeoutMs: Long,
        ) = Unit

        override suspend fun getHome(sourceId: String): List<HomeComponent> {
            fetchStarted.complete(Unit)
            gate?.await()
            return returnedLayout
        }
    }

    private fun viewModel(repository: AnimeRepository) = HomeViewModel(context, repository, SourceStateStore(context), SavedStateHandle())

    private fun HomeViewModel.titles(): List<String?> =
        sourceDataMap.value
            .getValue(SOURCE)
            .home
            .map { it.title }

    private fun HomeViewModel.loading(): Boolean = sourceDataMap.value.getValue(SOURCE).isLoading

    @Test
    fun `streamed rows are visible while get_home is still running`() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeRepository(gate = gate)
            repository.returnedLayout = listOf(laid("Nổi bật"), laid("Mới cập nhật"), laid("Tiền chiếu"))
            val vm = viewModel(repository)

            vm.loadSourceData(SOURCE)
            repository.fetchStarted.await()

            // The fetch is still gated, so the only thing on screen is what the
            // source streamed — which is the entire reason this feature exists.
            host.partialHome(HomePartialResult.Component(sent("Nổi bật")))
            assertTrue("the load should not have finished", vm.loading())
            assertEquals(listOf("Nổi bật"), vm.titles())

            // A second row appends behind the first, in the order it was sent.
            host.partialHome(HomePartialResult.Component(sent("Mới cập nhật")))
            assertEquals(listOf("Nổi bật", "Mới cập nhật"), vm.titles())

            gate.complete(Unit)
            awaitUntil { !vm.loading() }
            assertEquals(
                "the returned layout must win over what was streamed",
                listOf("Nổi bật", "Mới cập nhật", "Tiền chiếu"),
                vm.titles(),
            )
        }

    @Test
    fun `the returned layout replaces the streamed rows without duplicating them`() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            // The source's own return value: the rows it already streamed, plus
            // one it only finished after it started sending.
            val repository = FakeRepository(gate = gate)
            repository.returnedLayout = listOf(laid("Nổi bật"), laid("Mới cập nhật"), laid("Tiền chiếu"))
            val vm = viewModel(repository)

            vm.loadSourceData(SOURCE)
            repository.fetchStarted.await()
            host.partialHome(HomePartialResult.Component(sent("Nổi bật")))
            host.partialHome(HomePartialResult.Component(sent("Mới cập nhật")))
            gate.complete(Unit)

            awaitUntil { !vm.loading() }
            assertEquals(
                listOf("Nổi bật", "Mới cập nhật", "Tiền chiếu"),
                vm.titles(),
            )
        }

    @Test
    fun `a source that streams nothing behaves exactly as before`() =
        runBlocking {
            val repository = FakeRepository()
            repository.returnedLayout = listOf(laid("only row"), laid("second"))
            val vm = viewModel(repository)

            vm.loadSourceData(SOURCE)
            awaitUntil { !vm.loading() }

            assertEquals(listOf("only row", "second"), vm.titles())
            assertFalse(vm.loading())
        }

    @Test
    fun `a row sent after the layout landed is ignored`() =
        runBlocking {
            val repository = FakeRepository()
            repository.returnedLayout = listOf(laid("only row"))
            val vm = viewModel(repository)

            vm.loadSourceData(SOURCE)
            awaitUntil { !vm.loading() }

            // A late chunk — a refresh already replaced the rows — must not
            // append to a settled layout.
            host.partialHome(HomePartialResult.Component(sent("ghost")))
            assertEquals(listOf("only row"), vm.titles())
        }

    @Test
    fun `an empty layout keeps what the source already streamed`() =
        runBlocking {
            // A source that streamed its rows and then returned nothing has still
            // told us what the page contains; blanking the screen would be worse
            // than showing what it sent. Gated so the row lands while the load is
            // still running, which is the state this is about.
            val gate = CompletableDeferred<Unit>()
            val repository = FakeRepository(gate = gate)
            val vm = viewModel(repository)

            vm.loadSourceData(SOURCE)
            repository.fetchStarted.await()
            host.partialHome(HomePartialResult.Component(sent("Nổi bật")))
            gate.complete(Unit)
            awaitUntil { !vm.loading() }

            assertEquals(listOf("Nổi bật"), vm.titles())
        }

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) yield()
        }
    }

    private companion object {
        const val SOURCE = "vi.animevietsub"
    }
}
