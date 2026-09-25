package git.shin.komorei

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.ui.screens.about.AboutScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Basic navigation/content coverage for the About page. */
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class AboutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsVersionAndSupportLinks() {
        composeTestRule.setContent { AboutScreen(onBack = {}) }

        composeTestRule.onNodeWithTag("about_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_github").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_discord").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_support").assertIsDisplayed()
    }

    @Test
    fun backButtonInvokesCallback() {
        var calls = 0
        composeTestRule.setContent { AboutScreen(onBack = { calls++ }) }

        composeTestRule.onNodeWithTag("about_back").performClick()

        assertEquals(1, calls)
    }
}
