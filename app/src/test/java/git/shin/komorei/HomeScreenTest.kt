package git.shin.komorei

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
import java.io.File

/**
 * Compose UI tests for [HomeScreen] — the main Home tab with the
 * source pager and filter header row.
 *
 * Uses a real [HomeViewModel] backed by the committed fake source.
 *
 * NOTE: [HomeScreen] is the Home TAB CONTENT — the app-shell navigation bars
 * (`tab_*` / `rail_tab_*`) live one level up in [git.shin.komorei.ui.screens.MainScreen],
 * so they are covered by MainScreenTest / MainScreenRailTest. What Home owns is
 * the source pager: its tab row and each source's own header actions.
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
            val host = KrxHostImpl(context)
            val registry = KrxSourceRegistry(context, host)
            registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
            repository = AnimeRepository(registry)
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
    fun homeScreenRendersSourceTabRow() {
        val vm = newViewModel()
        composeTestRule.setContent {
            HomeScreen(
                onAnimeClick = {},
                viewModel = vm,
                onOpenSearch = {},
            )
        }
        // The pager's source tab bar is Home's own primary navigation.
        composeTestRule
            .onNodeWithTag("source_tab_row")
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
        composeTestRule
            .onNodeWithTag("home_screen")
            .performClick()
    }

    @Test
    fun homeScreenNotificationsButtonOpensTheSubPage() {
        val vm = newViewModel()
        var opened = false
        composeTestRule.setContent {
            HomeScreen(
                onAnimeClick = {},
                viewModel = vm,
                onOpenSearch = {},
                onOpenNotifications = { opened = true },
            )
        }
        // Notifications is a sub-page off the Home header bell, not a tab.
        composeTestRule
            .onNodeWithTag("home_notifications_button")
            .performClick()
        assert(opened) { "bell click should invoke onOpenNotifications" }
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
        // The start page is the `all` AGGREGATOR, which hides the per-source
        // actions — switch to the real source page first, where
        // SourceHomeContent puts its search/filter entry (SourceSearchButton).
        composeTestRule
            .onNodeWithTag("source_tab_vi.fake-source")
            .performClick()
        composeTestRule
            .onNodeWithTag("source_search_button")
            .performClick()
    }
}
