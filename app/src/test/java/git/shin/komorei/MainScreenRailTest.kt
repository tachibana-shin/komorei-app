package git.shin.komorei

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.deeplink.DeepLinkManager
import git.shin.komorei.data.deeplink.DeepLinkResolver
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.deeplink.DeepLinkViewModel
import git.shin.komorei.ui.navigation.mainTabs
import git.shin.komorei.ui.player.PlayerViewModel
import git.shin.komorei.ui.screens.MainScreen
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
 * The tablet/landscape half of [MainScreen]'s shell: above the 600dp width
 * breakpoint it renders [git.shin.komorei.ui.components.MainNavigationRail]
 * instead of the bottom bar, so `main_navigation_rail` / `rail_tab_*` only
 * exist at this width. Kept in its own class because the device qualifier is a
 * class-level Robolectric knob; the phone bar is covered by [MainScreenTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class MainScreenRailTest {
    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var repository: AnimeRepository
    private lateinit var libraryRepository: LibraryRepository
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
            val db = Room.inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java).build()
            libraryRepository = LibraryRepository(db.animeDao())
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

    private fun newDeepLinkViewModel(): DeepLinkViewModel = DeepLinkViewModel(DeepLinkManager(), DeepLinkResolver(repository))

    @Test
    fun mainScreenRendersNavigationRailWithEveryTab() {
        composeTestRule.setContent {
            MainScreen(
                playerViewModel = newPlayerViewModel(),
                deepLinkViewModel = newDeepLinkViewModel(),
                homeViewModel = newHomeViewModel(),
            )
        }
        composeTestRule.onNodeWithTag("main_navigation_rail").assertIsDisplayed()
        mainTabs.forEach { tab ->
            composeTestRule.onNodeWithTag("rail_tab_${tab.key}").assertIsDisplayed()
        }
    }

    @Test
    fun mainScreenRailReSelectingHomeDoesNotCrash() {
        composeTestRule.setContent {
            MainScreen(
                playerViewModel = newPlayerViewModel(),
                deepLinkViewModel = newDeepLinkViewModel(),
                homeViewModel = newHomeViewModel(),
            )
        }
        composeTestRule.onNodeWithTag("rail_tab_home").performClick()
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }
}
