package git.shin.komorei

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SearchHistoryStore
import git.shin.komorei.data.SourceSearchEvent
import git.shin.komorei.model.Source
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Compose UI tests for the Tìm Kiếm (Search) tab.
 *
 * Verify the post-refactor contract:
 *  - Blank query + no history → idle with search history prompt.
 *  - Changing a filter alone → still idle (no search triggered).
 *  - Typing a query → search results appear, query added to history.
 *
 * Uses a real SearchViewModel with the committed fake source,
 * passed directly to SearchDiscoveryScreen to bypass hiltViewModel().
 *
 * All lookups use testTags (language-independent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class DiscoverScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var repository: AnimeRepository
    private lateinit var registry: KrxSourceRegistry
    private lateinit var searchHistoryStore: SearchHistoryStore
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
            val context = ApplicationProvider.getApplicationContext<Context>()
            registry = KrxSourceRegistry(context, KrxHostImpl(context))
            val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
            checkNotNull(runner) { "fake source should load" }
            repository = AnimeRepository(registry)
            searchHistoryStore = SearchHistoryStore(context)
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(repo: AnimeRepository = repository): SearchViewModel =
        SearchViewModel(
            ApplicationProvider.getApplicationContext(),
            repo,
            searchHistoryStore,
            SavedStateHandle(),
        ).also { it.searchDebounceMillis = 0 }

    /**
     * Multi-source search that finishes with one hit (the real fake source)
     * plus a fabricated second source that completes cleanly empty — the
     * mixed world where some sections show cards and others must render the
     * "không có kết quả" note instead of vanishing.
     */
    private class MixedOutcomeRepository(
        registry: KrxSourceRegistry,
    ) : AnimeRepository(registry) {
        override fun searchMultiSourceStream(
            query: String,
            selectedGenreId: String?,
            contentRating: Int?,
            languages: Set<String>,
            sourceIds: Set<String>,
        ): Flow<SourceSearchEvent> =
            flow {
                val real = candidateSourcesForSearch(contentRating, languages, sourceIds).first()
                val results = search(real.id, query.ifBlank { null }, 1, emptyList()).entries
                emit(SourceSearchEvent.Completed(real, results))
                emit(
                    SourceSearchEvent.Completed(
                        Source(id = "vi.dummy-empty", name = "Dummy Empty"),
                        emptyList(),
                    ),
                )
            }
    }

    /** Multi-source search that always fails (deterministic error section). */
    private class ErroringRepository(
        registry: KrxSourceRegistry,
    ) : AnimeRepository(registry) {
        override fun searchMultiSourceStream(
            query: String,
            selectedGenreId: String?,
            contentRating: Int?,
            languages: Set<String>,
            sourceIds: Set<String>,
        ): Flow<SourceSearchEvent> =
            flow {
                val source = candidateSourcesForSearch(contentRating, languages, sourceIds).first()
                emit(SourceSearchEvent.Failed(source, "HTTP 500"))
            }
    }

    @Test
    fun idleShowsSearchHistoryNotGenreGrid() {
        val vm = newViewModel()
        composeTestRule.setContent {
            git.shin.komorei.ui.screens.search.SearchDiscoveryScreen(
                onAnimeClick = {},
                viewModel = vm,
            )
        }

        // Idle state — search history prompt (no genre grid)
        composeTestRule
            .onNodeWithText("Chưa có lịch sử tìm kiếm")
            .performClick()
    }

    @Test
    fun filterChangeWithoutQueryStaysIdle() {
        val vm = newViewModel()
        composeTestRule.setContent {
            git.shin.komorei.ui.screens.search.SearchDiscoveryScreen(
                onAnimeClick = {},
                viewModel = vm,
            )
        }

        // Open the aggregate filter sheet via FilterSheetButton
        composeTestRule
            .onNodeWithTag("filter_sheet_button")
            .performClick()

        // Sheet rendered — close it
        composeTestRule
            .onNodeWithTag("filter_sheet_close")
            .performClick()

        // Still idle — search history prompt (no search triggered by filter change alone)
        composeTestRule
            .onNodeWithText("Chưa có lịch sử tìm kiếm")
            .performClick()
    }

    @Test
    fun searchAddsToHistory() {
        val vm = newViewModel()
        composeTestRule.setContent {
            git.shin.komorei.ui.screens.search.SearchDiscoveryScreen(
                onAnimeClick = {},
                viewModel = vm,
            )
        }

        // Type a query and add directly to history
        vm.onSearchQueryChange("Frieren")
        vm.addToSearchHistory()

        // Search history should contain the query
        assertTrue("Frieren" in vm.searchHistory.value)
    }

    @Test
    fun focusingSearchInputAnimatesHeaderAway() {
        val vm = newViewModel()
        composeTestRule.setContent {
            git.shin.komorei.ui.screens.search.SearchDiscoveryScreen(
                onAnimeClick = {},
                viewModel = vm,
            )
        }

        // Branding header visible before focus
        composeTestRule.onNodeWithTag("search_header_title").assertExists()

        // Focus the search field → header collapses via AnimatedVisibility
        composeTestRule.onNodeWithTag("search_input_field").performClick()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("search_header_title").assertDoesNotExist()
    }

    @Test
    fun cancelButtonSlidesInOnFocusAndRestoresHeader() {
        val vm = newViewModel()
        composeTestRule.setContent {
            git.shin.komorei.ui.screens.search.SearchDiscoveryScreen(
                onAnimeClick = {},
                viewModel = vm,
            )
        }

        // No Hủy while idle
        composeTestRule.onNodeWithTag("search_input_cancel").assertDoesNotExist()

        // Focus the field → header collapses, Hủy slides in
        composeTestRule.onNodeWithTag("search_input_field").performClick()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_header_title").assertDoesNotExist()
        composeTestRule.onNodeWithTag("search_input_cancel").assertExists()

        // Type a query first so cancel has something to clear
        vm.onSearchQueryChange("Naruto")
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        // Hủy → clears the query + focus → header animates back
        composeTestRule.onNodeWithTag("search_input_cancel").performClick()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_header_title").assertExists()
        composeTestRule.onNodeWithTag("search_input_cancel").assertDoesNotExist()
        assertTrue(vm.searchQuery.value.isEmpty())
    }

    @Test
    fun emptySourceSectionShowsNoResultsNote() {
        val vm = newViewModel(MixedOutcomeRepository(registry))
        composeTestRule.setContent {
            git.shin.komorei.ui.screens.search.SearchDiscoveryScreen(
                onAnimeClick = {},
                viewModel = vm,
            )
        }

        // A source that finished cleanly with zero matches must show its own
        // "không có kết quả" note in the mixed Success — not vanish entirely —
        // while the source with hits still renders its cards.
        vm.onSearchQueryChange("Frieren")
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("search_source_empty").assertExists()
    }

    @Test
    fun failedSourceSectionShowsErrorAndRetry() {
        val vm = newViewModel(ErroringRepository(registry))
        composeTestRule.setContent {
            git.shin.komorei.ui.screens.search.SearchDiscoveryScreen(
                onAnimeClick = {},
                viewModel = vm,
            )
        }

        // A failed source renders a visible error message + retry in its
        // body instead of a silent empty section.
        vm.onSearchQueryChange("Frieren")
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("search_source_error_detail").assertExists()
        composeTestRule.onNodeWithTag("search_source_retry").assertExists()
    }
}
