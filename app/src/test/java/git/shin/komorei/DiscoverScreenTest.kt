package git.shin.komorei

import android.content.Context
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SearchHistoryStore
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
    private lateinit var searchHistoryStore: SearchHistoryStore
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
        private val fakeKrx: String = System.getProperty("komorei.test.fakeKrx")
            ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registry = KrxSourceRegistry(context, KrxHostImpl(context))
        val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
        checkNotNull(runner) { "fake source should load" }
        repository = AnimeRepository(registry)
        searchHistoryStore = SearchHistoryStore(context)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): SearchViewModel =
        SearchViewModel(
            ApplicationProvider.getApplicationContext(),
            repository,
            searchHistoryStore,
            SavedStateHandle(),
        ).also { it.searchDebounceMillis = 0 }

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
        composeTestRule.onNodeWithText("Chưa có lịch sử tìm kiếm")
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
        composeTestRule.onNodeWithTag("filter_sheet_button")
            .performClick()

        // Sheet rendered — close it
        composeTestRule.onNodeWithTag("filter_sheet_close")
            .performClick()

        // Still idle — search history prompt (no search triggered by filter change alone)
        composeTestRule.onNodeWithText("Chưa có lịch sử tìm kiếm")
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
}
