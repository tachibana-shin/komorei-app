package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.navigation.SearchArgsCodec
import git.shin.komorei.ui.screens.search.SourceSearchViewModel
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
 * Per-source search state (Aidoku DynamicFilters): the source's `filters()`
 * and the debounced, paginated `search()` — on the REAL runner +
 * [KrxHostImpl] + the committed `fake-vi-source.krx` fixture.
 *
 * `searchDebounceMillis` is set to 0 because the test Main scheduler's virtual
 * clock never advances — a real 300ms debounce would never fire. The VM's
 * `viewModelScope` runs on an unconfined test Main dispatcher and the
 * repository suspends into the real runner threads, so assertions wait (real
 * time) for the flow to converge (same pattern as [HomeViewModelListingTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SourceSearchViewModelTest {

    private lateinit var repository: AnimeRepository
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

    private fun newViewModel(
        handle: SavedStateHandle = SavedStateHandle(mapOf("sourceId" to "vi.fake-source")),
    ): SourceSearchViewModel =
        SourceSearchViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            repository = repository,
            savedStateHandle = handle,
        ).also { it.searchDebounceMillis = 0 }

    @Test
    fun dynamicFiltersLoadAllKinds() = runBlocking {
        val vm = newViewModel()
        awaitUntil { vm.filters.value.size == 6 }

        val filters = vm.filters.value
        assertEquals(listOf("search", "sort", "genres", "status", "year"), filters.take(5).map { it.id })

        val sort = filters[1].kind as FilterKind.Sort
        assertEquals(listOf("Đánh giá", "Phổ biến", "A-Z"), sort.options)
        assertTrue("sort must be able to ascend", sort.canAscend)

        val genres = filters[2].kind as FilterKind.MultiSelect
        assertEquals(12, genres.options.size)
        assertTrue("genres render as tag chips", genres.usesTagStyle)

        val status = filters[3].kind as FilterKind.Select
        assertEquals(listOf("Tất cả", "Đang phát", "Hoàn thành"), status.options)

        val range = filters[4].kind as FilterKind.Range
        assertEquals(1996f, range.min ?: -1f, 0f)
        assertEquals(2025f, range.max ?: -1f, 0f)

        assertTrue("a note block ends the list", filters[5].kind is FilterKind.Note)
    }

    @Test
    fun blankQueryWithNoFiltersStaysIdle() = runBlocking {
        val vm = newViewModel()
        awaitUntil { vm.filters.value.size == 6 }

        // Nothing typed and no filters → no search call, pure idle screen.
        assertTrue(vm.uiState.value.isIdle)
        vm.onQueryChange("   ")
        awaitUntil { vm.uiState.value.isIdle }
        assertTrue(vm.uiState.value.items.isEmpty())

        // Typing a real query searches; clearing drops back to idle (no fetch).
        vm.onQueryChange("frieren")
        awaitUntil { vm.uiState.value.items.size == 1 }
        vm.clearQuery()
        awaitUntil { vm.uiState.value.isIdle }
        assertTrue(vm.uiState.value.items.isEmpty())
        assertEquals(0, vm.uiState.value.loadedPage)
    }

    @Test
    fun querySearchReturnsMatches() = runBlocking {
        val vm = newViewModel()
        awaitUntil { vm.filters.value.size == 6 }

        vm.onQueryChange("Frieren")
        awaitUntil { vm.uiState.value.items.isNotEmpty() }

        val state = vm.uiState.value
        assertEquals(1, state.items.size)
        assertEquals("Frieren: Pháp Sư Tiễn Táng", state.items.first().title)
        assertFalse(state.hasNextPage)
    }

    @Test
    fun selectFilterCompletedPaginates() = runBlocking {
        val vm = newViewModel()
        awaitUntil { vm.filters.value.size == 6 }

        // Select "Hoàn thành" — 18 completed entries, page size 15.
        vm.setFilterValue("status", FilterValue.Select("status", "Hoàn thành"))
        awaitUntil { vm.uiState.value.items.isNotEmpty() }

        var state = vm.uiState.value
        assertEquals(15, state.items.size)
        assertTrue(state.hasNextPage)

        vm.loadMore()
        awaitUntil { vm.uiState.value.items.size > 15 }
        state = vm.uiState.value
        assertEquals(18, state.items.size)
        assertFalse(state.hasNextPage)
        assertEquals(2, state.loadedPage)
    }

    @Test
    fun genreIncludeFiltersResults() = runBlocking {
        val vm = newViewModel()
        awaitUntil { vm.filters.value.size == 6 }

        vm.setFilterValue("genres", FilterValue.MultiSelect("genres", listOf("Hành Động"), emptyList()))
        awaitUntil { vm.uiState.value.items.isNotEmpty() }

        val state = vm.uiState.value
        assertTrue("action titles must exist", state.items.isNotEmpty())
        // The fake source OR-matches included genres on the Lite card's genres.
        assertTrue(
            "every result must carry the included genre",
            state.items.all { anime -> anime.genres.any { it.name == "Hành Động" } },
        )

        // Combined with a status filter the result narrows further.
        vm.setFilterValue("status", FilterValue.Select("status", "Hoàn thành"))
        awaitUntil { vm.uiState.value.items.size < state.items.size }
        assertTrue(vm.uiState.value.items.isNotEmpty())
    }

    @Test
    fun sortByPopularityLeadsWithOnePiece() = runBlocking {
        val vm = newViewModel()
        awaitUntil { vm.filters.value.size == 6 }

        // sort index 1 = Phổ biến → views descending → One Piece (62M) first.
        vm.setFilterValue("sort", FilterValue.Sort("sort", 1, false))
        awaitUntil { vm.uiState.value.items.isNotEmpty() }

        assertTrue(vm.uiState.value.items.size in 2..15)
        assertEquals("One Piece: Hải Tặc Đại Chiến", vm.uiState.value.items.first().title)
        // First page descending by views.
        val views = vm.uiState.value.items.map { it.views }
        assertEquals(views.sortedDescending(), views)
    }

    @Test
    fun restoresQueryFromRouteArgs() = runBlocking {
        // A recreated VM re-reads the query/filters from the entry's state.
        val handle = SavedStateHandle(mapOf("sourceId" to "vi.fake-source", "query" to "Frieren"))
        val vm = newViewModel(handle)

        assertEquals("Frieren", vm.query.value)
        awaitUntil { vm.uiState.value.items.size == 1 }
        assertEquals("Frieren: Pháp Sư Tiễn Táng", vm.uiState.value.items.first().title)
    }

    @Test
    fun restoresFiltersFromRouteArgs() = runBlocking {
        val handle = SavedStateHandle(
            mapOf(
                "sourceId" to "vi.fake-source",
                "filters" to SearchArgsCodec.toJson(listOf(FilterValue.Select("status", "Hoàn thành"))),
            ),
        )
        val vm = newViewModel(handle)

        assertEquals(listOf(FilterValue.Select("status", "Hoàn thành")), vm.enabledFilters.value)
        awaitUntil { vm.uiState.value.items.size == 15 }
        assertEquals(15, vm.uiState.value.items.size)
        assertTrue(vm.uiState.value.hasNextPage)
        assertEquals(listOf(FilterValue.Select("status", "Hoàn thành")), vm.enabledFilters.value)
    }

    @Test
    fun mirrorsChangesToSavedStateHandle() = runBlocking {
        val handle = SavedStateHandle(mapOf("sourceId" to "vi.fake-source"))
        val vm = newViewModel(handle)

        vm.onQueryChange("frieren")
        assertEquals("frieren", handle.get<String>("query"))

        vm.setFilterValue("status", FilterValue.Select("status", "Hoàn thành"))
        assertEquals(
            listOf(FilterValue.Select("status", "Hoàn thành")),
            SearchArgsCodec.fromJson(handle.get<String>("filters")),
        )

        vm.resetAllFilters()
        assertEquals(emptyList<FilterValue>(), SearchArgsCodec.fromJson(handle.get<String>("filters")))
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(15_000) {
            while (!condition()) delay(50)
        }
    }
}