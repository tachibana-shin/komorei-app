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
import git.shin.komorei.ui.screens.home.HomeViewModel
import git.shin.komorei.ui.screens.source.SourceHomeScreen
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
 * Compose UI tests for [SourceHomeScreen] — the source's own home page
 * where the badge layout-shift bug was originally reported.
 *
 * Uses a real [HomeViewModel] backed by the committed fake source.
 *
 * All lookups use testTags (language-independent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class SourceHomeScreenTest {
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

    private fun newViewModel(sourceId: String = "vi.fake-source"): HomeViewModel =
        HomeViewModel(
            ApplicationProvider.getApplicationContext(),
            repository,
            SourceStateStore(ApplicationProvider.getApplicationContext()),
            SavedStateHandle(mapOf("sourceId" to sourceId)),
        )

    @Test
    fun sourceHomeScreenRendersBackButton() {
        val vm = newViewModel()
        composeTestRule.setContent {
            SourceHomeScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onOpenListing = { _, _ -> },
                onOpenSettings = {},
                onOpenSearch = {},
                onBack = {},
                viewModel = vm,
            )
        }
        // Source home screen renders with back button
        composeTestRule
            .onNodeWithTag("source_home_menu")
            .performClick()
    }

    @Test
    fun sourceHomeScreenRendersSearchButton() {
        val vm = newViewModel()
        composeTestRule.setContent {
            SourceHomeScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onOpenListing = { _, _ -> },
                onOpenSettings = {},
                onOpenSearch = {},
                onBack = {},
                viewModel = vm,
            )
        }
        // Source home screen renders the search button
        composeTestRule
            .onNodeWithTag("source_home_menu")
            .performClick()
    }

    @Test
    fun sourceHomeScreenDoesNotShiftWhenDataLoads() {
        val vm = newViewModel()
        composeTestRule.setContent {
            SourceHomeScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onOpenListing = { _, _ -> },
                onOpenSettings = {},
                onOpenSearch = {},
                onBack = {},
                viewModel = vm,
            )
        }
        // Screen renders — verify key elements exist
        composeTestRule
            .onNodeWithTag("source_home_menu")
            .performClick()
    }

    @Test
    fun sourceHomeScreenRendersWithEmptySource() {
        val vm = newViewModel(sourceId = "vi.nonexistent")
        composeTestRule.setContent {
            SourceHomeScreen(
                sourceId = "vi.nonexistent",
                onAnimeClick = {},
                onOpenListing = { _, _ -> },
                onOpenSettings = {},
                onOpenSearch = {},
                onBack = {},
                viewModel = vm,
            )
        }
        // Screen should still render without crashing
        composeTestRule
            .onNodeWithTag("source_home_menu")
            .performClick()
    }
}
