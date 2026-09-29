package git.shin.komorei

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.update.UpdateInfo
import git.shin.komorei.data.update.UpdateUiState
import git.shin.komorei.ui.screens.update.UpdateBottomSheet
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The update sheet.
 *
 * The changelog is the content here, and the point of the sheet rather than a
 * dialog is that the content can be a scrollable column without pushing the
 * buttons out of reach. So what is pinned is the version, the rendered notes,
 * and the two answers — plus the one case where the sheet must *not* offer an
 * answer at all: once the APK is downloading, the installer takes over, and a
 * dismiss button there would abandon a download whose result the reader cannot
 * see.
 */
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class UpdateBottomSheetTest {
    // The android variant, not the plain one: the markdown renderer is a
    // `TextView` inside an `AndroidView`, so proving the notes were parsed
    // means reading that view, and that needs the host activity's decor view.
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** A real release body, in the shape semantic-release emits it. */
    private val notes =
        """
        ## [1.1.8](https://github.com/tachibana-shin/komorei-app/compare/v1.1.7...v1.1.8) (2026-09-29)

        ### Bug Fixes

        * let an installed source actually be updated ([2565f3b](https://github.com/tachibana-shin/komorei-app/commit/2565f3b))
        """.trimIndent()

    private fun info(
        version: String = "1.1.8",
        releaseNotes: String = notes,
    ) = UpdateInfo(
        version = version,
        releaseNotes = releaseNotes,
        downloadUrl = "https://example.invalid/a.apk",
        assetId = 1L,
        expectedSize = 1L,
        sha256 = "a".repeat(64),
    )

    @Test
    fun showsTheVersionAndTheChangelog() {
        composeTestRule.setContent {
            UpdateBottomSheet(
                state = UpdateUiState.Available(info()),
                onDismiss = {},
                onConfirm = {},
            )
        }
        composeTestRule.onNodeWithTag("update_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_sheet_version").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_sheet_notes").assertIsDisplayed()
    }

    /**
     * The notes are actually parsed as Markdown.
     *
     * Asserted against the rendered [android.widget.TextView] rather than
     * through Compose semantics, because `compose-markdown` renders into a
     * `TextView` inside an `AndroidView` — its text is a platform view, not a
     * semantics text node, so `onNodeWithText` cannot see it and a test written
     * that way would pass for the wrong reasons. Reading the view is also the
     * stronger claim: it proves the string reached a renderer and came out as
     * text, rather than being displayed verbatim.
     *
     * The bullet marker is the tell. Rendered as Markdown the `*` is structure
     * and does not survive into the text; dumped raw, it would.
     */
    @Test
    fun theChangelogIsRenderedAsMarkdownNotAsRawText() {
        composeTestRule.setContent {
            UpdateBottomSheet(
                state = UpdateUiState.Available(info()),
                onDismiss = {},
                onConfirm = {},
            )
        }
        val rendered = markdownViewText()
        assertTrue(
            "the section heading should be rendered, got: $rendered",
            rendered.contains("Bug Fixes"),
        )
        assertTrue(
            "the bullet text should be rendered, got: $rendered",
            rendered.contains("let an installed source actually be updated"),
        )
        assertTrue(
            "the version heading should be rendered, got: $rendered",
            rendered.contains("1.1.8"),
        )
        assertTrue(
            "a raw bullet marker means the notes were not parsed, got: $rendered",
            !rendered.contains("* "),
        )
    }

    /**
     * Every text the markdown renderer produced, joined.
     *
     * Read out of the sheet's own dialog window, not the activity's decor view:
     * a `ModalBottomSheet` is a platform dialog, so its content — including the
     * library's `TextView` — is composed into a different window and is not
     * reachable from the activity. The activity's tree under Robolectric shows
     * only the `AndroidComposeView` with nothing beneath it, which is why the
     * first attempt at this found no `TextView` at all.
     */
    private fun markdownViewText(): String {
        composeTestRule.waitForIdle()
        val root =
            org.robolectric.shadows.ShadowDialog
                .getLatestDialog()
                ?.window
                ?.decorView
                ?: error("the sheet's dialog window is not shown")

        val texts = mutableListOf<String>()

        fun walk(v: android.view.View) {
            (v as? android.widget.TextView)?.let { texts += it.text?.toString().orEmpty() }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(root)
        val all = texts.filter { it.isNotBlank() }.joinToString("\n")
        assertTrue(
            "the markdown renderer produced no text; found ${texts.size} text view(s)",
            all.isNotBlank(),
        )
        return all
    }

    @Test
    fun emptyNotesSaySoRatherThanShowingNothing() {
        composeTestRule.setContent {
            UpdateBottomSheet(
                state = UpdateUiState.Available(info(releaseNotes = "")),
                onDismiss = {},
                onConfirm = {},
            )
        }
        composeTestRule.onNodeWithTag("update_sheet_notes").assertIsDisplayed()
    }

    @Test
    fun laterAndInstallAreBothOfferedWhileWaiting() {
        composeTestRule.setContent {
            UpdateBottomSheet(
                state = UpdateUiState.Available(info()),
                onDismiss = {},
                onConfirm = {},
            )
        }
        composeTestRule.onNodeWithTag("update_sheet_later").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_sheet_install").assertIsDisplayed()
    }

    @Test
    fun tappingInstallAsksForTheDownload() {
        var confirmed = false
        composeTestRule.setContent {
            UpdateBottomSheet(
                state = UpdateUiState.Available(info()),
                onDismiss = {},
                onConfirm = { confirmed = true },
            )
        }
        composeTestRule.onNodeWithTag("update_sheet_install").performClick()
        assertTrue("tapping install must ask for the download", confirmed)
    }

    @Test
    fun tappingLaterAsksForTheDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            UpdateBottomSheet(
                state = UpdateUiState.Available(info()),
                onDismiss = { dismissed = true },
                onConfirm = {},
            )
        }
        composeTestRule.onNodeWithTag("update_sheet_later").performClick()
        assertTrue("tapping later must dismiss", dismissed)
    }

    @Test
    fun whileDownloadingOnlyProgressIsShown() {
        composeTestRule.setContent {
            UpdateBottomSheet(
                state = UpdateUiState.Downloading(info(), 42),
                onDismiss = {},
                onConfirm = {},
            )
        }
        // Existence, not visibility: the progress bar is a zero-content row and
        // "displayed" is a claim about pixels that says nothing useful here.
        composeTestRule.onNodeWithTag("update_sheet_progress").assertExists()
        // No dismiss while a download is in flight: the installer takes over
        // next, and dismissing here would drop a transfer the reader cannot
        // then follow.
        composeTestRule.onNodeWithTag("update_sheet_later").assertDoesNotExist()
        composeTestRule.onNodeWithTag("update_sheet_install").assertDoesNotExist()
    }

    @Test
    fun nothingIsShownWhenThereIsNoUpdate() {
        composeTestRule.setContent {
            UpdateBottomSheet(
                state = UpdateUiState.Idle,
                onDismiss = {},
                onConfirm = {},
            )
        }
        composeTestRule.onNodeWithTag("update_sheet").assertDoesNotExist()
    }
}
