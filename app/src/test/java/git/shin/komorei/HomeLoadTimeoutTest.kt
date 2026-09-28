package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.model.HomeComponent
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.home.HomeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A source's `get_home` that never comes back.
 *
 * The page must still reach a state the reader can act on. `getHome` blocks on
 * the runner, the runner blocks on a native call, and no coroutine cancellation
 * can interrupt either — so a source that simply never returns used to leave the
 * skeleton up for good, with no error, no retry and no way to tell that anything
 * had gone wrong. The HTTP client now caps each request, which covers a site
 * that is merely slow; this covers the case that survives that, a source that
 * spins inside wasm.
 *
 * Driven on virtual time so the real budget is exercised rather than a test-only
 * seam: `runTest` skips the delay, and the assertion is that the load lands in
 * an error state at all — not that it took a particular wall-clock time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeLoadTimeoutTest {
    private lateinit var context: Context

    companion object {
        private const val SOURCE = "vi.hangs"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Standard, not Unconfined: the budget is a `delay`, and only a
        // standard dispatcher makes that skippable.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A repository whose `get_home` never returns. */
    private inner class HangingRepository : AnimeRepository(KrxSourceRegistry(context, KrxHostImpl(context))) {
        val started = CompletableDeferred<Unit>()

        override fun partialHomeResults(sourceId: String): Flow<HomeComponent> = emptyFlow()

        override suspend fun awaitPartialHomeSubscribers(
            sourceId: String,
            timeoutMs: Long,
        ) = Unit

        override suspend fun getHome(sourceId: String): List<HomeComponent> {
            started.complete(Unit)
            awaitCancellation()
        }
    }

    private fun viewModel(repository: AnimeRepository) = HomeViewModel(context, repository, SourceStateStore(context), SavedStateHandle())

    private fun HomeViewModel.data() = sourceDataMap.value.getValue(SOURCE)

    private fun HomeViewModel.loading(): Boolean = data().isLoading

    private fun HomeViewModel.error(): String? = data().error

    private suspend fun awaitUntil(condition: () -> Boolean) {
        var spins = 0
        while (!condition() && spins < 400) {
            yield()
            spins++
        }
    }

    @Test
    fun `a home load that never returns settles into an error`() =
        runTest {
            val repository = HangingRepository()
            val vm = viewModel(repository)

            vm.loadSourceData(SOURCE)
            repository.started.await()

            // Still loading: the source really is stuck, which is the state that
            // used to be terminal.
            assertTrue("the load should be in flight", vm.loading())

            // Let virtual time pass the budget.
            advanceTimeBy(180_000)
            awaitUntil { !vm.loading() }

            assertFalse("the skeleton must not be left up forever", vm.loading())
            assertNotNull("a stuck load must say so, not fail silently", vm.error())
        }
}
