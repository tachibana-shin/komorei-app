package git.shin.komorei

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.LibraryRepository
import git.shin.komorei.data.local.KomoreiDatabase
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import git.shin.komorei.ui.screens.insights.InsightsScreen
import git.shin.komorei.ui.screens.insights.InsightsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class InsightsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: KomoreiDatabase
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KomoreiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun emptyHistoryShowsEmptyState() {
        val viewModel = InsightsViewModel(LibraryRepository(database.animeDao()))
        composeTestRule.setContent { InsightsScreen(onBack = {}, viewModel = viewModel) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("insights_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("insights_empty").assertIsDisplayed()
    }

    @Test
    fun savedHistoryRendersStats() {
        val now = System.currentTimeMillis()
        runBlocking {
            database.animeDao().upsertWatchHistory(
                WatchHistoryEntity(
                    animeId = "anime",
                    sourceId = "source",
                    episodeId = "episode",
                    episodeNumber = "1",
                    episodeTitle = "",
                    lastWatchedAt = now,
                    progressMs = 60 * 60 * 1000L,
                    durationMs = 60 * 60 * 1000L,
                ),
            )
        }
        val viewModel = InsightsViewModel(LibraryRepository(database.animeDao()))
        composeTestRule.setContent { InsightsScreen(onBack = {}, viewModel = viewModel) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("insights_stat_episodes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("insights_stat_series").assertIsDisplayed()
        composeTestRule.onNodeWithTag("insights_stat_hours").assertIsDisplayed()
    }
}
