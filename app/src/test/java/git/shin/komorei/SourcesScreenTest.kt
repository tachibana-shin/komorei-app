package git.shin.komorei

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.SourceStateStore
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import git.shin.komorei.ui.screens.sources.SourcesScreen
import git.shin.komorei.ui.screens.sources.SourcesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Compose UI tests for [SourcesScreen] — the "Nguồn" tab with
 * the source list, search, and add-source functionality.
 *
 * Uses a real [SourcesViewModel] backed by the committed fake source.
 *
 * All lookups use testTags (language-independent).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class SourcesScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var repository: AnimeRepository
    private lateinit var sourcesViewModel: SourcesViewModel
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
            val db =
                Room
                    .inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            repository = AnimeRepository(registry)
            val stateStore = SourceStateStore(context)
            sourcesViewModel =
                SourcesViewModel(
                    context,
                    repository,
                    registry,
                    stateStore,
                    git.shin.komorei.data
                        .SourceReposRepository(OkHttpClient(), ApplicationProvider.getApplicationContext()),
                )
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sourcesScreenRendersScreenTag() {
        composeTestRule.setContent {
            SourcesScreen(
                onOpenRepos = {},
                onOpenSource = {},
                viewModel = sourcesViewModel,
            )
        }
        composeTestRule
            .onNodeWithTag("sources_screen")
            .performClick()
    }

    @Test
    fun sourcesScreenRendersSourceRow() {
        composeTestRule.setContent {
            SourcesScreen(
                onOpenRepos = {},
                onOpenSource = {},
                viewModel = sourcesViewModel,
            )
        }
        // The fake source should appear in the list
        composeTestRule
            .onNodeWithTag("source_row_vi.fake-source")
            .performClick()
    }

    @Test
    fun sourcesScreenRendersRefreshButton() {
        composeTestRule.setContent {
            SourcesScreen(
                onOpenRepos = {},
                onOpenSource = {},
                viewModel = sourcesViewModel,
            )
        }
        composeTestRule
            .onNodeWithTag("sources_refresh_button")
            .performClick()
    }

    @Test
    fun sourcesScreenRendersAddSourceButton() {
        composeTestRule.setContent {
            SourcesScreen(
                onOpenRepos = {},
                onOpenSource = {},
                viewModel = sourcesViewModel,
            )
        }
        composeTestRule
            .onNodeWithTag("sources_add_source_button")
            .performClick()
    }

    @Test
    fun sourcesScreenSwitchingTabsDoesNotCrash() {
        composeTestRule.setContent {
            SourcesScreen(
                onOpenRepos = {},
                onOpenSource = {},
                viewModel = sourcesViewModel,
            )
        }
        // Click the fake source row
        composeTestRule
            .onNodeWithTag("source_row_vi.fake-source")
            .performClick()
        // Click refresh
        composeTestRule
            .onNodeWithTag("sources_refresh_button")
            .performClick()
    }
}
