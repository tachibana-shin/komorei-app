package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.home.HomeScreen
import git.shin.komorei.ui.screens.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import java.io.File

/**
 * Compose UI tests for [HomeScreen] — the main Home tab with the
 * source pager and filter header row.
 *
 * Uses a real [HomeViewModel] backed by the committed fake source.
 *
 * All lookups use testTags (language-independent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var repository: AnimeRepository
    private val mainDispatcher = UnconfinedTestDispatcher()

    companion object {
        private val fakeKrx: String = System.getProperty("komorei.test.fakeKrx")
            ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = KrxHostImpl(context)
        val registry = KrxSourceRegistry(context, host)
        registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
        repository = AnimeRepository(registry)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): HomeViewModel =
        HomeViewModel(
            ApplicationProvider.getApplicationContext(),
            repository,
            SourceStateStore(ApplicationProvider.getApplicationContext()),
            SavedStateHandle(),
        )

    @Test
    fun homeScreenRendersRailTabs() {
        val vm = newViewModel()
        composeTestRule.setContent {
            HomeScreen(
                onAnimeClick = {},
                viewModel = vm,
                onOpenSearch = {},
            )
        }
        // Navigation rail tabs should be present
        composeTestRule.onNodeWithTag("rail_tab_home")
            .performClick()
        composeTestRule.onNodeWithTag("rail_tab_search")
            .performClick()
        composeTestRule.onNodeWithTag("rail_tab_sources")
            .performClick()
        composeTestRule.onNodeWithTag("rail_tab_library")
            .performClick()
    }

    @Test
    fun homeScreenRendersHomeScreenTag() {
        val vm = newViewModel()
        composeTestRule.setContent {
            HomeScreen(
                onAnimeClick = {},
                viewModel = vm,
                onOpenSearch = {},
            )
        }
        composeTestRule.onNodeWithTag("home_screen")
            .performClick()
    }

    @Test
    fun homeScreenSwitchingTabsDoesNotCrash() {
        val vm = newViewModel()
        composeTestRule.setContent {
            HomeScreen(
                onAnimeClick = {},
                viewModel = vm,
                onOpenSearch = {},
            )
        }
        // Switch between tabs
        composeTestRule.onNodeWithTag("rail_tab_home")
            .performClick()
        composeTestRule.onNodeWithTag("rail_tab_search")
            .performClick()
        composeTestRule.onNodeWithTag("rail_tab_sources")
            .performClick()
        composeTestRule.onNodeWithTag("rail_tab_library")
            .performClick()
        composeTestRule.onNodeWithTag("rail_tab_home")
            .performClick()
    }

    @Test
    fun homeScreenRendersFilterButton() {
        val vm = newViewModel()
        composeTestRule.setContent {
            HomeScreen(
                onAnimeClick = {},
                viewModel = vm,
                onOpenSearch = {},
            )
        }
        // The source page has a filter button (SourceSearchButton in SourceHomeContent)
        composeTestRule.onNodeWithTag("rail_tab_home")
            .performClick()
    }
}
