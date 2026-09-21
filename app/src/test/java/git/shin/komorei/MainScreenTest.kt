package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.sdk.KrxHostImpl
import androidx.room.Room
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.MainScreen
import git.shin.komorei.ui.screens.home.HomeViewModel
import git.shin.komorei.ui.player.PlayerViewModel
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
 * Compose UI tests for [MainScreen] — the root navigation shell
 * with rail tabs on tablets and bottom nav on phones.
 *
 * Uses a real [HomeViewModel] backed by the committed fake source.
 *
 * All lookups use testTags (language-independent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class MainScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var repository: AnimeRepository
    private lateinit var libraryRepository: LibraryRepository
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
        val db = Room.inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java).build()
        libraryRepository = LibraryRepository(db.animeDao())
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newHomeViewModel(): HomeViewModel =
        HomeViewModel(
            ApplicationProvider.getApplicationContext(),
            repository,
            SourceStateStore(ApplicationProvider.getApplicationContext()),
            SavedStateHandle(),
        )

    private fun newPlayerViewModel(): PlayerViewModel =
        PlayerViewModel(
            ApplicationProvider.getApplicationContext(),
            repository,
            libraryRepository,
            SavedStateHandle(),
        )

    @Test
    fun mainScreenRendersAllRailTabs() {
        val vm = newHomeViewModel()
        composeTestRule.setContent {
            MainScreen(
                playerViewModel = newPlayerViewModel(),
            )
        }
        // All four rail tabs should be present
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
    fun mainScreenRendersNavigationRail() {
        val vm = newHomeViewModel()
        composeTestRule.setContent {
            MainScreen(
                playerViewModel = newPlayerViewModel(),
            )
        }
        composeTestRule.onNodeWithTag("main_navigation_rail")
            .performClick()
    }

    @Test
    fun mainScreenRendersBottomNavigation() {
        val vm = newHomeViewModel()
        composeTestRule.setContent {
            MainScreen(
                playerViewModel = newPlayerViewModel(),
            )
        }
        composeTestRule.onNodeWithTag("main_bottom_navigation")
            .performClick()
    }

    @Test
    fun mainScreenSwitchingTabsDoesNotCrash() {
        val vm = newHomeViewModel()
        composeTestRule.setContent {
            MainScreen(
                playerViewModel = newPlayerViewModel(),
            )
        }
        // Navigate through all tabs
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
    fun mainScreenRendersHomeTab() {
        val vm = newHomeViewModel()
        composeTestRule.setContent {
            MainScreen(
                playerViewModel = newPlayerViewModel(),
            )
        }
        composeTestRule.onNodeWithTag("tab_home")
            .performClick()
    }

    @Test
    fun mainScreenRendersSearchTab() {
        val vm = newHomeViewModel()
        composeTestRule.setContent {
            MainScreen(
                playerViewModel = newPlayerViewModel(),
            )
        }
        composeTestRule.onNodeWithTag("tab_search")
            .performClick()
    }
}
