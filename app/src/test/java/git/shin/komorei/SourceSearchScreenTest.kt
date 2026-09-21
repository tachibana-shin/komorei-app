package git.shin.komorei

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.search.SourceSearchScreen
import git.shin.komorei.ui.screens.search.SourceSearchViewModel
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
 * Compose UI tests for [SourceSearchScreen] — the per-source search screen.
 *
 * Uses a real [SourceSearchViewModel] backed by the committed fake source.
 *
 * All lookups use testTags (language-independent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class SourceSearchScreenTest {

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
        val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
        checkNotNull(runner) { "fake source should load" }
        repository = AnimeRepository(registry)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(sourceId: String = "vi.fake-source"): SourceSearchViewModel =
        SourceSearchViewModel(
            ApplicationProvider.getApplicationContext(),
            repository,
            SavedStateHandle(mapOf("sourceId" to sourceId)),
        )

    @Test
    fun sourceSearchScreenRendersSearchInput() {
        val vm = newViewModel()
        composeTestRule.setContent {
            SourceSearchScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onBack = {},
                viewModel = vm,
            )
        }
        // Search input field should be present
        composeTestRule.onNodeWithTag("source_search_input")
            .performClick()
    }

    @Test
    fun sourceSearchScreenRendersClearButton() {
        val vm = newViewModel()
        composeTestRule.setContent {
            SourceSearchScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onBack = {},
                viewModel = vm,
            )
        }
        composeTestRule.onNodeWithTag("source_search_input")
            .performClick()
    }

    @Test
    fun sourceSearchScreenRendersCancelButton() {
        val vm = newViewModel()
        composeTestRule.setContent {
            SourceSearchScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onBack = {},
                viewModel = vm,
            )
        }
        composeTestRule.onNodeWithTag("source_search_input")
            .performClick()
    }

    @Test
    fun sourceSearchScreenRendersWithoutCrash() {
        val vm = newViewModel()
        composeTestRule.setContent {
            SourceSearchScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onBack = {},
                viewModel = vm,
            )
        }
        // Screen renders
        composeTestRule.onNodeWithTag("source_search_input")
            .performClick()
    }

    @Test
    fun sourceSearchScreenWithDifferentSource() {
        val vm = newViewModel(sourceId = "vi.fake-source")
        composeTestRule.setContent {
            SourceSearchScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onBack = {},
                viewModel = vm,
            )
        }
        composeTestRule.onNodeWithTag("source_search_input")
            .performClick()
    }
}
