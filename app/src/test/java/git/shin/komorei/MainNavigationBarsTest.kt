package git.shin.komorei

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.ui.components.MainBottomNavigation
import git.shin.komorei.ui.components.MainNavigationRail
import git.shin.komorei.ui.navigation.mainTabs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct tests for the app-shell navigation bars.
 *
 * Both bars are pure `tabs` + `currentRoute` + `onSelect` composables driven
 * off the single [mainTabs] list, so their behaviour (every destination
 * present, click emits the right tab) is asserted here without standing up
 * the Hilt ViewModel graph that [MainScreen] needs. MainScreenTest /
 * MainScreenRailTest cover the shell that hosts them at each breakpoint.
 *
 * The bottom bar is a FIXED evenly-spaced row with no horizontal scroll, so
 * every tab is reachable on the phone width; the rail is a LazyColumn that
 * auto-scrolls the selection into view.
 */
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class MainNavigationBarsTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun bottomNavigationRendersEveryTab() {
        composeTestRule.setContent {
            MainBottomNavigation(
                tabs = mainTabs,
                currentRoute = mainTabs.first().route,
                onSelect = {},
            )
        }
        composeTestRule.onNodeWithTag("main_bottom_navigation").assertIsDisplayed()
        mainTabs.forEach { tab ->
            composeTestRule.onNodeWithTag("tab_${tab.key}").assertIsDisplayed()
        }
    }

    @Test
    fun bottomNavigationEmitsSelectedTab() {
        var selectedKey: String? = null
        composeTestRule.setContent {
            MainBottomNavigation(
                tabs = mainTabs,
                currentRoute = mainTabs.first().route,
                onSelect = { selectedKey = it.key },
            )
        }
        mainTabs.forEach { tab ->
            composeTestRule.onNodeWithTag("tab_${tab.key}").performClick()
            assertEquals("clicking ${tab.key} must emit that tab", tab.key, selectedKey)
        }
    }

    @Test
    fun navigationRailRendersEveryTab() {
        composeTestRule.setContent {
            MainNavigationRail(
                tabs = mainTabs,
                currentRoute = mainTabs.first().route,
                onSelect = {},
            )
        }
        composeTestRule.onNodeWithTag("main_navigation_rail").assertIsDisplayed()
        mainTabs.forEach { tab ->
            composeTestRule.onNodeWithTag("rail_tab_${tab.key}").assertIsDisplayed()
        }
    }

    @Test
    fun navigationRailEmitsSelectedTab() {
        var selectedKey: String? = null
        composeTestRule.setContent {
            MainNavigationRail(
                tabs = mainTabs,
                currentRoute = mainTabs.last().route,
                onSelect = { selectedKey = it.key },
            )
        }
        mainTabs.forEach { tab ->
            composeTestRule.onNodeWithTag("rail_tab_${tab.key}").performClick()
            assertEquals("clicking ${tab.key} must emit that tab", tab.key, selectedKey)
        }
    }
}
