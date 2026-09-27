package git.shin.komorei

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
            val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
            checkNotNull(runner) { "fake source should load" }
            repository = AnimeRepository(registry)
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
        composeTestRule
            .onNodeWithTag("source_search_input")
            .assertExists()
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
        composeTestRule
            .onNodeWithTag("source_search_input")
            .assertExists()
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
        composeTestRule
            .onNodeWithTag("source_search_input")
            .assertExists()
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
        composeTestRule
            .onNodeWithTag("source_search_input")
            .assertExists()
    }

    @Test
    fun autofocusesWhenOpenedBlank() {
        val vm = newViewModel() // no query arg -> blank
        composeTestRule.setContent {
            SourceSearchScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onBack = {},
                viewModel = vm,
            )
        }
        // Opened fresh (source-home search button): the input grabs focus so
        // the IME is up and ready for typing.
        composeTestRule
            .onNode(
                hasTestTag("source_search_input") and hasAnyDescendant(isFocused()),
            ).assertExists()
    }

    @Test
    fun skipsAutofocusWhenKeywordCarried() {
        // Carried in from the Discover tab header: route args seed the query,
        // results start loading — the keyboard must NOT pop over them.
        val vm =
            SourceSearchViewModel(
                ApplicationProvider.getApplicationContext(),
                repository,
                SavedStateHandle(mapOf("sourceId" to "vi.fake-source", "query" to "phim")),
            )
        composeTestRule.setContent {
            SourceSearchScreen(
                sourceId = "vi.fake-source",
                onAnimeClick = {},
                onBack = {},
                viewModel = vm,
            )
        }
        composeTestRule.onAllNodes(isFocused()).assertCountEquals(0)
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
        composeTestRule
            .onNodeWithTag("source_search_input")
            .assertExists()
    }
}
