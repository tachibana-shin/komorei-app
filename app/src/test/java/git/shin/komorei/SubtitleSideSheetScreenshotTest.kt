package git.shin.komorei

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import git.shin.komorei.ui.player.FloatingMiniPlayer
import git.shin.komorei.ui.player.SkipKind
import git.shin.komorei.ui.player.components.PlayerSideSheet
import git.shin.komorei.ui.player.components.SegmentedProgressSlider
import git.shin.komorei.ui.player.components.SkipSegmentPill
import git.shin.komorei.ui.player.components.TrackSelectionPane
import git.shin.komorei.ui.player.computeMiniPlayerGeometry
import git.shin.komorei.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class SubtitleSideSheetScreenshotTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun subtitle_side_sheet() {
        composeTestRule.setContent {
            MyApplicationTheme {
                // Landscape phone: the subtitle side sheet is the fullscreen player's
                // right-hand 320dp panel with the TrackSelectionPane as its content.
                Box(modifier = Modifier.size(800.dp, 360.dp)) {
                    PlayerSideSheet(visible = true, onDismiss = {}) {
                        TrackSelectionPane(
                            title = stringResource(R.string.player_subtitle),
                            type = C.TRACK_TYPE_TEXT,
                            availableTracks = null,
                            onTrackSelected = { _, _ -> },
                            onClearTrack = {},
                            onBack = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/subtitle_side_sheet.png")
    }

    @Test
    fun segmented_slider_with_intro_outro() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.size(800.dp, 96.dp)) {
                    SegmentedProgressSlider(
                        positionMs = 540_000L,
                        durationMs = 1_440_000L, // 24 min episode (fake data)
                        bufferedPositionMs = 900_000L,
                        introRange = 10_000L..90_000L, // bài hát mở đầu
                        outroRange = 1_350_000L..1_430_000L, // bài hát kết
                        onSeek = {},
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/segmented_slider_intro_outro.png")
    }

    @Test
    fun skip_pills() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.size(360.dp, 160.dp)) {
                    SkipSegmentPill(kind = SkipKind.INTRO, onClick = {})
                    Spacer(modifier = Modifier.height(12.dp))
                    SkipSegmentPill(kind = SkipKind.OUTRO, onClick = {})
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/skip_pills.png")
    }

    @Test
    fun floating_mini_player() {
        composeTestRule.setContent {
            MyApplicationTheme {
                // Phone-sized surface: the collapsed floating PiP bubble should land
                // bottom-right, 16:9, bigger than before, with only play/pause (top-left)
                // + close (top-right), no progress bar, and stay fully inside the screen
                // (px→dp sizing bug made it density× too big and shoved it off-screen).
                val density = LocalDensity.current
                val initialPosition =
                    computeMiniPlayerGeometry(
                        maxWidthPx = with(density) { 411.dp.toPx() },
                        maxHeightPx = with(density) { 914.dp.toPx() },
                        density = density,
                    ).cornerBottomRight
                Box(modifier = Modifier.size(411.dp, 914.dp)) {
                    FloatingMiniPlayer(
                        player = null,
                        posterUrl = "",
                        isPlaying = true,
                        initialPosition = initialPosition,
                        onPositionChange = {},
                        onExpand = {},
                        onPlayPauseToggle = {},
                        onClose = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Geometry sanity: the bubble must be a ~42% 16:9 box fully inside the phone
        // surface, sitting in the bottom-right corner (px vs dp units bug made it
        // density× too big and shoved it off-screen).
        val bounds = composeTestRule.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        check(bounds.width in 380f..560f) { "bubble width ${bounds.width} out of range" }
        check(bounds.height in 214f..320f) { "bubble height ${bounds.height} out of range" }
        check(bounds.left > 450f) { "bubble not bottom-right" }
        check(bounds.top > 1400f) { "bubble not bottom-right" }
        check(bounds.right <= 1080f) { "bubble overflows right edge" }
        check(bounds.bottom <= 2400f) { "bubble overflows bottom edge" }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/floating_mini_player.png")
    }
}
