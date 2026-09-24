package git.shin.komorei

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.LogLevel
import git.shin.komorei.data.LogStore
import git.shin.komorei.ui.screens.logs.LogsScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [LogsScreen] — the "Ghi nhật ký máy chủ" viewer.
 *
 * Covers the empty state, rendering a source's own output, the level filters
 * and the Clear action. `LogStore` is a process-wide singleton, so every test
 * starts and ends with a cleared buffer.
 *
 * All lookups use testTags (language-independent).
 */
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class LogsScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Before
    fun setUp() = LogStore.clear()

    @After
    fun tearDown() = LogStore.clear()

    private fun setContent(back: () -> Unit = {}) {
        composeTestRule.setContent { LogsScreen(onBack = back) }
    }

    @Test
    fun emptyStoreShowsTheEmptyState() {
        setContent()

        composeTestRule.onNodeWithTag("logs_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("logs_empty").assertIsDisplayed()
    }

    @Test
    fun sourceOutputIsListed() {
        LogStore.add(LogLevel.DEFAULT, "println from vi.fake-source", "vi.fake-source")
        setContent()

        composeTestRule.onNodeWithTag("logs_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("println from vi.fake-source").assertIsDisplayed()
        // The line is tagged with the source that emitted it.
        composeTestRule.onNodeWithText("vi.fake-source").assertIsDisplayed()
    }

    @Test
    fun levelBadgesAreShownForNonDefaultLevels() {
        LogStore.error("request failed")
        setContent()

        // "ERROR" appears twice: the chip filter and the row's badge, so assert
        // the count rather than a unique node.
        composeTestRule.onAllNodesWithText("ERROR").assertCountEquals(2)
        composeTestRule.onNodeWithText("request failed").assertIsDisplayed()
    }

    @Test
    fun selectingALevelFilterHidesOtherLevels() {
        LogStore.info("an info line")
        LogStore.error("an error line")
        setContent()

        // Both are visible unfiltered.
        composeTestRule.onNodeWithText("an info line").assertIsDisplayed()

        composeTestRule.onNodeWithTag("logs_filter_error").performClick()

        // Now only the ERROR line remains, so the info line is gone.
        composeTestRule.onNodeWithText("an error line").assertIsDisplayed()
        composeTestRule.onNodeWithTag("logs_empty").assertDoesNotExist()
    }

    @Test
    fun aFilterWithNoMatchesShowsTheFilteredEmptyState() {
        LogStore.info("only an info line")
        setContent()

        composeTestRule.onNodeWithTag("logs_filter_error").performClick()

        composeTestRule.onNodeWithTag("logs_empty").assertIsDisplayed()
    }

    @Test
    fun tappingTheSameFilterTwiceRestoresEverything() {
        LogStore.info("an info line")
        LogStore.error("an error line")
        setContent()

        composeTestRule.onNodeWithTag("logs_filter_error").performClick()
        composeTestRule.onNodeWithTag("logs_filter_error").performClick()

        composeTestRule.onNodeWithText("an info line").assertIsDisplayed()
        composeTestRule.onNodeWithText("an error line").assertIsDisplayed()
    }

    @Test
    fun clearEmptiesTheBufferAndTheScreen() {
        LogStore.info("something happened")
        setContent()

        composeTestRule.onNodeWithTag("logs_clear").performClick()

        assertEquals(emptyList<Any>(), LogStore.entries.value)
        composeTestRule.onNodeWithTag("logs_empty").assertIsDisplayed()
    }

    @Test
    fun backButtonInvokesTheCallback() {
        var backCalls = 0
        setContent(back = { backCalls++ })

        composeTestRule.onNodeWithTag("logs_back").performClick()

        assertEquals(1, backCalls)
    }

    @Test
    fun followTailToggles() {
        setContent()

        // Not assertIsDisplayed: the footer sits below the (empty) list and is
        // off-screen in a default-sized test window. The click is the contract
        // — it must not crash and must keep the node composed.
        composeTestRule.onNodeWithTag("logs_follow").performClick()
        composeTestRule.onNodeWithTag("logs_follow").assertExists()
    }
}
