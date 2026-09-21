package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.library.LibraryScreen
import git.shin.komorei.ui.screens.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * Compose UI tests for [LibraryScreen] — the library tab with
 * following/history sub-tabs.
 *
 * Uses an in-memory Room database for the library repository.
 *
 * All lookups use testTags (language-independent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var libraryViewModel: LibraryViewModel
    private lateinit var database: KomoreiDatabase

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
        database = Room.inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val animeRepository = AnimeRepository(registry)
        libraryViewModel = LibraryViewModel(
            LibraryRepository(database.animeDao()),
            animeRepository,
        )
    }

    @Test
    fun libraryScreenRendersScreenTag() {
        composeTestRule.setContent {
            LibraryScreen(
                onAnimeClick = {},
                viewModel = libraryViewModel,
            )
        }
        composeTestRule.onNodeWithTag("library_screen")
            .performClick()
    }

    @Test
    fun libraryScreenRendersSubTabs() {
        composeTestRule.setContent {
            LibraryScreen(
                onAnimeClick = {},
                viewModel = libraryViewModel,
            )
        }
        composeTestRule.onNodeWithTag("library_subtab_0")
            .performClick()
        composeTestRule.onNodeWithTag("library_subtab_1")
            .performClick()
    }

    @Test
    fun libraryScreenSwitchingSubTabsDoesNotCrash() {
        composeTestRule.setContent {
            LibraryScreen(
                onAnimeClick = {},
                viewModel = libraryViewModel,
            )
        }
        composeTestRule.onNodeWithTag("library_subtab_0")
            .performClick()
        composeTestRule.onNodeWithTag("library_subtab_1")
            .performClick()
        composeTestRule.onNodeWithTag("library_subtab_0")
            .performClick()
    }

    @Test
    fun libraryScreenRendersWithoutCrash() {
        composeTestRule.setContent {
            LibraryScreen(
                onAnimeClick = {},
                viewModel = libraryViewModel,
            )
        }
        composeTestRule.onNodeWithTag("library_screen")
            .performClick()
    }
}
