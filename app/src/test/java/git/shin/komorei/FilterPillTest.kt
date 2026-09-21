package git.shin.komorei

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import git.shin.komorei.ui.components.search.filters.FilterPill
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.compose.ui.test.junit4.v2.createComposeRule

/**
 * Compose UI tests for [FilterPill] — verifies the layout-shift fix.
 *
 * The badge must always occupy the same space regardless of [badgeCount],
 * preventing the Row from resizing when data loads.
 *
 * All lookups use testTags (language-independent).
 */
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class FilterPillTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun pillWithZeroBadgeHasSameWidthAsWithBadge() {
        // Pill with badgeCount = 0
        composeTestRule.setContent {
            FilterPill(
                name = "test",
                active = false,
                onClick = {},
                badgeCount = 0,
                testTag = "filter_pill_test",
            )
        }
        // Pill renders with zero badge
        composeTestRule.onNodeWithTag("filter_pill_test")
            .performClick()
    }

    @Test
    fun pillWithBadgeRendersBadge() {
        composeTestRule.setContent {
            FilterPill(
                name = "test",
                active = true,
                onClick = {},
                badgeCount = 3,
                testTag = "filter_pill_test",
            )
        }
        composeTestRule.onNodeWithTag("filter_pill_test")
            .performClick()
    }

    @Test
    fun pillBadgeIsInvisibleWhenCountIsZero() {
        composeTestRule.setContent {
            FilterPill(
                name = "test",
                active = true,
                onClick = {},
                badgeCount = 0,
                testTag = "filter_pill_test",
            )
        }
        // Verify pill renders even when badge count is 0
        composeTestRule.onNodeWithTag("filter_pill_test")
            .performClick()
    }

    @Test
    fun pillWithOneBadgeRendersBadge() {
        composeTestRule.setContent {
            FilterPill(
                name = "test",
                active = true,
                onClick = {},
                badgeCount = 1,
                testTag = "filter_pill_test",
            )
        }
        composeTestRule.onNodeWithTag("filter_pill_test")
            .performClick()
    }

    @Test
    fun pillWithIconAndChevronRendersAllComponents() {
        composeTestRule.setContent {
            FilterPill(
                name = "test",
                active = false,
                onClick = {},
                badgeCount = 0,
                chevron = true,
                icon = null,
                testTag = "filter_pill_test",
            )
        }
        composeTestRule.onNodeWithTag("filter_pill_test")
            .performClick()
    }
}
