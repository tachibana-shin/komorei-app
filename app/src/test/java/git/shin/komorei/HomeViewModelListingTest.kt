package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.home.HomeViewModel
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
 * Home-listing state (Aidoku "get_dynamic_listings" chips + inline page view)
 * on the REAL runner + [KrxHostImpl] + the committed `fake-vi-source.krx`
 * fixture. The VM's `viewModelScope` runs on an unconfined test Main
 * dispatcher and the repository hops to real runner threads, so assertions
 * wait (real time) for the flow to converge.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeViewModelListingTest {
    private lateinit var repository: AnimeRepository
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
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
            val registry = KrxSourceRegistry(context, KrxHostImpl(context))
            val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
            assertNotNull("fake source should load", runner)
            repository = AnimeRepository(registry)
            Dispatchers.setMain(mainDispatcher)
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun listingChipsLoadAndInlinePageSwap() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val vm = HomeViewModel(context, repository, SourceStateStore(context), SavedStateHandle())

            // Chips fetch (get_dynamic_listings) → shown once the page is visible.
            vm.loadListings("vi.fake-source")
            awaitUntil {
                vm.listingStateMap.value["vi.fake-source"]
                    ?.listings
                    ?.size == 4
            }
            var state = vm.listingStateMap.value.getValue("vi.fake-source")
            assertEquals(
                listOf("latest", "popular", "ongoing", "completed"),
                state.listings.map { it.id },
            )
            assertEquals(0, state.selectedIndex) // HOME selected by default

            // Tap the "Mới nhất" chip → page 1 swaps the content below to the listing.
            vm.selectListing("vi.fake-source", 1)
            awaitUntil {
                vm.listingStateMap.value["vi.fake-source"]!!
                    .page.items.size == 15
            }
            state = vm.listingStateMap.value.getValue("vi.fake-source")
            assertEquals(1, state.selectedIndex)
            assertEquals(1, state.page.loadedPage)
            assertTrue(state.page.hasNextPage)

            // Infinite scroll → page 2 appended until the catalog ends.
            vm.loadListingMore("vi.fake-source")
            awaitUntil {
                vm.listingStateMap.value["vi.fake-source"]!!
                    .page.items.size == 22
            }
            state = vm.listingStateMap.value.getValue("vi.fake-source")
            assertEquals(2, state.page.loadedPage)
            assertFalse(state.page.hasNextPage)

            // HOME chip restores the home page.
            vm.selectListing("vi.fake-source", 0)
            assertEquals(
                0,
                vm.listingStateMap.value
                    .getValue("vi.fake-source")
                    .selectedIndex,
            )
        }

    @Test
    fun switchingChipsLoadsEachListingFromPageOne() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val vm = HomeViewModel(context, repository, SourceStateStore(context), SavedStateHandle())

            vm.loadListings("vi.fake-source")
            awaitUntil {
                vm.listingStateMap.value["vi.fake-source"]
                    ?.listings
                    ?.size == 4
            }

            // Load "Mới nhất" (page 1, 15 items → loadedPage == 1).
            vm.selectListing("vi.fake-source", 1)
            awaitUntil {
                vm.listingStateMap.value["vi.fake-source"]!!
                    .page.items.size == 15
            }
            assertEquals(
                1,
                vm.listingStateMap.value
                    .getValue("vi.fake-source")
                    .page.loadedPage,
            )

            // Switch to "Đang phát" (a tiny ~4-item catalog). The old code reused
            // loadedPage=1 and fetched page 2 → EMPTY. Each reset must start at 1.
            vm.selectListing("vi.fake-source", 3)
            awaitUntil {
                vm.listingStateMap.value["vi.fake-source"]!!
                    .page.items
                    .isNotEmpty()
            }
            var state = vm.listingStateMap.value.getValue("vi.fake-source")
            assertEquals(3, state.selectedIndex)
            assertEquals(1, state.page.loadedPage)
            assertTrue("ongoing must load its own page-1 items", state.page.items.isNotEmpty())

            // And every further switch STILL loads (no page-stale pollution).
            vm.selectListing("vi.fake-source", 4)
            awaitUntil {
                vm.listingStateMap.value["vi.fake-source"]!!
                    .page.items
                    .isNotEmpty()
            }
            state = vm.listingStateMap.value.getValue("vi.fake-source")
            assertEquals(1, state.page.loadedPage)
            assertTrue(state.page.items.isNotEmpty())

            vm.selectListing("vi.fake-source", 1)
            awaitUntil {
                vm.listingStateMap.value["vi.fake-source"]!!
                    .page.items.size == 15
            }
            state = vm.listingStateMap.value.getValue("vi.fake-source")
            assertEquals(1, state.page.loadedPage)
        }

    @Test
    fun aggregatorListingsUnion() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val vm = HomeViewModel(context, repository, SourceStateStore(context), SavedStateHandle())

            vm.loadListings("all")
            awaitUntil {
                vm.listingStateMap.value["all"]
                    ?.listings
                    ?.isNotEmpty() == true
            }
            val state = vm.listingStateMap.value.getValue("all")
            assertEquals(
                listOf("latest", "popular", "ongoing", "completed"),
                state.listings.map { it.id },
            )
        }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(15_000) {
            while (!condition()) delay(50)
        }
    }
}
