package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.ContentRatingFilter
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.search.SearchUiState
import git.shin.komorei.ui.screens.search.SearchViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Discover (Khám Phá) global search — the Aidoku-style filters (content rating
 * / language / sources) plus the genre shortcut and query, on the REAL runner +
 * [KrxHostImpl] + the committed `fake-vi-source.krx` fixture.
 *
 * `searchDebounceMillis` is set to 0 because the test Main scheduler's virtual
 * clock never advances — a real 300ms debounce would never fire (same pattern
 * as [SourceSearchViewModelTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchViewModelTest {

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

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): SearchViewModel =
        SearchViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            repository = repository,
            savedStateHandle = handle,
        ).also { it.searchDebounceMillis = 0 }

    private suspend fun currentSuccess(vm: SearchViewModel): SearchUiState.Success {
        awaitUntil { vm.searchUiState.value is SearchUiState.Success }
        return vm.searchUiState.value as SearchUiState.Success
    }

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
    }

    @Test
    fun filterOnlySafeRatingSearchesCatalog() = runBlocking {
        val vm = newViewModel()

        // The fake source is safe (contentRating = 0) → blank query + SAFE
        // opens its full catalog (page size 15), mirroring per-source filters.
        vm.setContentRating(ContentRatingFilter.SAFE)
        val state = currentSuccess(vm)

        assertEquals(15, state.totalCount)
    }

    @Test
    fun nsfwRatingExcludesSafeSources() = runBlocking {
        val vm = newViewModel()

        vm.setContentRating(ContentRatingFilter.NSFW)
        val state = currentSuccess(vm)

        assertEquals("no 18+ source installed", 0, state.totalCount)
    }

    @Test
    fun languageFilterKeepsMatchingSource() = runBlocking {
        val vm = newViewModel()

        // "en" and "vi" are both published by the fake source → still searched.
        vm.setLanguage("en")
        val state = currentSuccess(vm)

        assertEquals(15, state.totalCount)
    }

    @Test
    fun languageFilterDropsSourcesWithoutTheCode() = runBlocking {
        val vm = newViewModel()

        // "ja" is not among the fake source's languages (vi/en) → no candidates.
        vm.setLanguage("ja")
        val state = currentSuccess(vm)

        assertEquals(0, state.totalCount)
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

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(15_000) {
            while (!condition()) delay(50)
        }
    }
}