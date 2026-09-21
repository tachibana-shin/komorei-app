package git.shin.komorei

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.ui.components.dialogs.ChallengeBypassDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for the visible-browser challenge dialog
 * ([ChallengeBypassDialog]) — the human fallback that pops when the headless
 * WebView cannot clear a JS challenge. Lookups use testTags (language-independent).
 */
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class ChallengeBypassDialogTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `continue button completes the bypass`() {
        var next = false
        var dismissed = false
        composeTestRule.setContent {
            ChallengeBypassDialog(
                url = "https://example.com/login",
                onNext = { next = true },
                onDismiss = { dismissed = true },
            )
        }
        composeTestRule.onNodeWithTag("challenge_continue", useUnmergedTree = true).performClick()
        assertTrue("continue must complete the bypass", next)
        assertTrue("continue must not dismiss the dialog itself", !dismissed)
    }

    @Test
    fun `close buttons dismiss without completing`() {
        var next = false
        var dismissed = false
        composeTestRule.setContent {
            ChallengeBypassDialog(
                url = "https://example.com/login",
                onNext = { next = true },
                onDismiss = { dismissed = true },
            )
        }
        composeTestRule.onNodeWithTag("challenge_dismiss", useUnmergedTree = true).performClick()
        assertTrue("close must dismiss the dialog", dismissed)
        assertTrue("close must not complete the bypass", !next)
    }
}