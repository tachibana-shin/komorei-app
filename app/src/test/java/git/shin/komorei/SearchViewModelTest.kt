package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SearchHistoryStore
import git.shin.komorei.data.SourceSearchEvent
import git.shin.komorei.model.ContentRatingFilter
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.search.SearchUiState
import git.shin.komorei.ui.screens.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tìm Kiếm global search — the Aidoku-style filters (content rating
 * / language / sources) plus the genre shortcut and query, on the REAL runner +
 * [KrxHostImpl] + the committed `fake-vi-source.krx` fixture.
 *
 * `searchDebounceMillis` is set to 0 because the test Main scheduler's virtual
 * clock never advances — a real 300ms debounce would never fire (same pattern
 * as [SourceSearchViewModelTest]).
 *
 * **Idle rule:** a blank query with no genre selected → idle with search history prompt.
 * Filters alone (rating/language/sources) do NOT trigger a search — they
 * only refine an active query+genre search, matching Aidoku's behaviour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchViewModelTest {

    private lateinit var repository: AnimeRepository
    private lateinit var registry: KrxSourceRegistry
    private lateinit var searchHistoryStore: SearchHistoryStore
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
        registry = KrxSourceRegistry(context, KrxHostImpl(context))
        val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
        assertNotNull("fake source should load", runner)
        repository = AnimeRepository(registry)
        searchHistoryStore = SearchHistoryStore(context)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): SearchViewModel =
        SearchViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            repository = repository,
            searchHistoryStore = searchHistoryStore,
            savedStateHandle = handle,
        ).also { it.searchDebounceMillis = 0 }

    private suspend fun currentSuccess(vm: SearchViewModel): SearchUiState.Success {
        awaitUntil { vm.searchUiState.value is SearchUiState.Success }
        return vm.searchUiState.value as SearchUiState.Success
    }

    // ── query-driven search ──────────────────────────────────────────

    @Test
    fun querySearchMergesAcrossSources() = runBlocking {
        val vm = newViewModel()

        vm.onSearchQueryChange("Frieren")
        val state = currentSuccess(vm)

        assertEquals(1, state.totalCount)
        assertEquals(
            "Frieren: Pháp Sư Tiễn Táng",
            state.resultsBySource.values.flatten().single().title,
        )
        // No source errors on a successful search.
        assertTrue(state.sourceErrors.isEmpty())
    }

    @Test
    fun queryReturnsPerSourceSections() = runBlocking {
        val vm = newViewModel()

        vm.onSearchQueryChange("Frieren")
        val state = currentSuccess(vm)

        // Each source that returned results appears as its own section.
        assertTrue("should have at least one source section", state.resultsBySource.isNotEmpty())
        assertTrue("no errors expected", state.sourceErrors.isEmpty())
        state.resultsBySource.forEach { (source, animes) ->
            assertTrue("source ${source.name} should have anime", animes.isNotEmpty())
        }
    }

    // ── idle rule: filters alone do NOT trigger search ───────────────

    @Test
    fun filterOnlyStaysIdle() = runBlocking {
        val vm = newViewModel()

        // Changing ONLY a filter with a blank query → stays idle.
        vm.setContentRating(ContentRatingFilter.SAFE)
        awaitUntil { vm.searchUiState.value is SearchUiState.Idle }
        assertTrue("rating-only change must stay idle", vm.searchUiState.value is SearchUiState.Idle)

        // Language change also stays idle.
        vm.setLanguage("en")
        awaitUntil { vm.searchUiState.value is SearchUiState.Idle }
        assertTrue("language-only change must stay idle", vm.searchUiState.value is SearchUiState.Idle)

        // Source filter change also stays idle.
        vm.setSourceFilter(setOf("vi.fake-source"))
        awaitUntil { vm.searchUiState.value is SearchUiState.Idle }
        assertTrue("source-only change must stay idle", vm.searchUiState.value is SearchUiState.Idle)
    }

    @Test
    fun genreShortcutTriggersSearch() = runBlocking {
        val vm = newViewModel()

        // Genre is like a query — triggers search even without a keyword.
        val action = vm.genres.first { it.name == "Hành Động" }
        vm.selectGenre(action)
        val state = currentSuccess(vm)

        assertTrue("genre must trigger search", state.totalCount > 0)
    }

    // ── query + filter refinement ────────────────────────────────────

    @Test
    fun queryWithSafeRatingReturnsResults() = runBlocking {
        val vm = newViewModel()

        vm.onSearchQueryChange("Frieren")
        vm.setContentRating(ContentRatingFilter.SAFE)
        val state = currentSuccess(vm)

        assertEquals(1, state.totalCount)
        assertTrue(state.resultsBySource.isNotEmpty())
        assertTrue(state.sourceErrors.isEmpty())
    }

    @Test
    fun languageFilterRefinesQueryResults() = runBlocking {
        val vm = newViewModel()

        vm.onSearchQueryChange("Frieren")
        // "en" and "vi" are both published by the fake source → still searched.
        vm.setLanguage("en")
        val state = currentSuccess(vm)

        assertEquals(1, state.totalCount)
        assertTrue(state.resultsBySource.isNotEmpty())
    }

    @Test
    fun wrongLanguageYieldsNoResults() = runBlocking {
        val vm = newViewModel()

        vm.onSearchQueryChange("Frieren")
        // "ja" is not among the fake source's languages (vi/en) → no candidates.
        vm.setLanguage("ja")
        val state = currentSuccess(vm)

        assertEquals(0, state.totalCount)
        assertTrue(state.resultsBySource.isEmpty())
    }

    @Test
    fun sourceFilterWhitelistsSources() = runBlocking {
        val vm = newViewModel()
        vm.onSearchQueryChange("Frieren")

        // Restricting to the installed source still finds it.
        vm.setSourceFilter(setOf("vi.fake-source"))
        awaitUntil { (vm.searchUiState.value as? SearchUiState.Success)?.totalCount == 1 }

        // A whitelist with no installed source yields no results.
        vm.setSourceFilter(setOf("some.other.source"))
        val state = currentSuccess(vm)
        assertEquals(0, state.totalCount)
        assertTrue(state.resultsBySource.isEmpty())
    }

    @Test
    fun genreSelectionSearchesTranslatedGenre() = runBlocking {
        val vm = newViewModel()
        val action = vm.genres.first { it.name == "Hành Động" }

        vm.selectGenre(action)
        val state = currentSuccess(vm)

        assertTrue(state.totalCount > 0)
        // buildGenreFilter maps the genre name into the source's MultiSelect
        // "genres" value — every result must carry the genre (would fail if the
        // filter never reached the wasm and the whole catalog came back).
        assertTrue(
            state.resultsBySource.values.flatten().all { anime ->
                anime.genres.any { it.name == "Hành Động" }
            },
        )
    }

    // ── per-source error tracking ────────────────────────────────────

    @Test
    fun sourceErrorsTrackedInState() = runBlocking {
        val vm = newViewModel()

        vm.onSearchQueryChange("Frieren")
        val state = currentSuccess(vm)

        // With the single installed source, there should be no errors.
        assertTrue(state.sourceErrors.isEmpty())
    }

    // ── idle → query → idle transitions ─────────────────────────────

    @Test
    fun clearSearchReturnsToIdle() = runBlocking {
        val vm = newViewModel()
        vm.onSearchQueryChange("frieren")
        awaitUntil { vm.searchUiState.value is SearchUiState.Success }

        vm.clearSearch()
        awaitUntil { vm.searchUiState.value is SearchUiState.Idle }
        assertTrue(vm.searchQuery.value.isEmpty())

        // A blank query with default filters must not fire a fetch.
        vm.setContentRating(ContentRatingFilter.ALL)
        vm.setSourceFilter(emptySet())
        vm.setLanguage(null)
        awaitUntil { vm.searchUiState.value is SearchUiState.Idle }
    }

    /**
     * [AnimeRepository] whose FIRST multi-source search throws a
     * [CancellationException] out of the stream — the same signal a
     * superseded in-flight search job produces when `searchJob.cancel()`
     * lands on its suspended `collect()` (a cancel lands on the suspend
     * point inside the stream → propagates out of `collect` → into the VM's
     * catch). This is the signal the fixed catch must rethrow instead of
     * turning into `SearchUiState.Error` ("StandaloneCoroutine was
     * cancelled"). Later calls delegate to the real implementation.
     */
    private class CancellationThrowingRepository(registry: KrxSourceRegistry) : AnimeRepository(registry) {
        private var thrown = false
        override fun searchMultiSourceStream(
            query: String,
            selectedGenreId: String?,
            contentRating: Int?,
            languages: Set<String>,
            sourceIds: Set<String>,
        ): Flow<SourceSearchEvent> = if (!thrown) {
            thrown = true
            flow { throw CancellationException("search superseded — in-flight job cancelled") }
        } else {
            super.searchMultiSourceStream(query, selectedGenreId, contentRating, languages, sourceIds)
        }
    }

    @Test
    fun cancelledSearchNeverSurfacesAsError() = runBlocking {
        val vm = SearchViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            repository = CancellationThrowingRepository(registry),
            searchHistoryStore = searchHistoryStore,
            savedStateHandle = SavedStateHandle(),
        ).also { it.searchDebounceMillis = 0 }

        // Record every UI state the VM emits so the transient cancellation
        // error is caught (the collector runs on the VM's own unconfined
        // dispatcher — StateFlow conflates for a slow one).
        val states = mutableListOf<SearchUiState>()
        val collector = launch(UnconfinedTestDispatcher(mainDispatcher.scheduler)) {
            vm.searchUiState.collect { states += it }
        }

        // First search stream throws CancellationException → the job is
        // superseded, exactly like typing a new query over a running search.
        vm.onSearchQueryChange("Frieren")
        vm.onSearchQueryChange("Frieren 2")
        awaitUntil { vm.searchUiState.value is SearchUiState.Success }

        collector.cancel()

        assertTrue(
            "a cancelled search job must never surface as Error; observed: $states",
            states.none { it is SearchUiState.Error },
        )
        assertTrue(vm.searchUiState.value is SearchUiState.Success)
    }

    // ── restore / persistence ────────────────────────────────────────

    @Test
    fun restoresSearchParamsFromSavedState() = runBlocking {
        // A recreated VM (tab switch / process death) reads the mirrored keys.
        val handle = SavedStateHandle(
            mapOf(
                "search_query" to "Frieren",
                "search_rating" to ContentRatingFilter.SAFE.name,
                "search_language" to "vi",
                "search_sources" to "vi.fake-source",
            ),
        )
        val vm = newViewModel(handle)

        assertEquals("Frieren", vm.searchQuery.value)
        assertEquals(ContentRatingFilter.SAFE, vm.contentRating.value)
        assertEquals("vi", vm.language.value)
        assertEquals(setOf("vi.fake-source"), vm.sourceFilter.value)

        // The restored params drive a real search — not the idle state.
        val state = currentSuccess(vm)
        assertEquals(1, state.totalCount)
        // Sections present.
        assertTrue(state.resultsBySource.isNotEmpty())
        assertTrue(state.sourceErrors.isEmpty())
    }

    @Test
    fun mirrorsSearchParamsToSavedState() = runBlocking {
        val handle = SavedStateHandle()
        val vm = newViewModel(handle)

        vm.onSearchQueryChange("Frieren")
        vm.setContentRating(ContentRatingFilter.SAFE)
        vm.setLanguage("vi")
        vm.setSourceFilter(setOf("vi.fake-source"))
        vm.selectGenre(vm.genres.first { it.name == "Hành Động" })

        assertEquals("Frieren", handle.get<String>("search_query"))
        assertEquals(ContentRatingFilter.SAFE.name, handle.get<String>("search_rating"))
        assertEquals("vi", handle.get<String>("search_language"))
        assertEquals("vi.fake-source", handle.get<String>("search_sources"))
        assertEquals(vm.selectedGenre.value?.id, handle.get<String>("search_genre"))

        // Clearing wipes the persisted state too.
        vm.clearSearch()
        assertEquals("", handle.get<String>("search_query"))
        assertEquals(null, handle.get<String>("search_rating"))
        assertEquals(null, handle.get<String>("search_language"))
        assertEquals(null, handle.get<String>("search_sources"))
        assertEquals(null, handle.get<String>("search_genre"))
    }

    // ── genre shortcut keeps idle when toggled off ───────────────────

    @Test
    fun genreToggleOffReturnsIdle() = runBlocking {
        val vm = newViewModel()

        // Select genre → search fires.
        val action = vm.genres.first { it.name == "Hành Động" }
        vm.selectGenre(action)
        awaitUntil { vm.searchUiState.value is SearchUiState.Success }
        assertTrue(vm.searchUiState.value is SearchUiState.Success)

        // Deselect → idle again.
        vm.selectGenre(action)
        awaitUntil { vm.searchUiState.value is SearchUiState.Idle }
        assertTrue("deselecting genre must return to idle", vm.searchUiState.value is SearchUiState.Idle)
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(15_000) {
            while (!condition()) delay(50)
        }
    }
}
