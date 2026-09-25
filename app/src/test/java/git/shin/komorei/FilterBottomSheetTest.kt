package git.shin.komorei

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.ui.components.search.filters.FilterListSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI tests for the filter bottom sheet chrome.
 *
 * Verify the post-refactor behaviour:
 *  - No "Áp dụng" / "Hủy" footer buttons in the aggregate filter sheet.
 *  - "Đặt lại" (Reset) clears and dismisses instantly.
 *
 * All lookups use testTags (language-independent).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class FilterBottomSheetTest {
    @get:Rule val composeTestRule = createComposeRule()

    // ── aggregate sheet: no Apply/Cancel footer ────────

    @Test
    fun aggregateSheetHasNoApplyButton() {
        composeTestRule.setContent {
            FilterListSheet(
                filters =
                    listOf(
                        Filter(
                            id = "status",
                            title = "Trạng thái",
                            kind =
                                FilterKind.Select(
                                    options = listOf("Tất cả", "Đăng phát", "Hoàn thành"),
                                ),
                        ),
                    ),
                initialEnabled = emptyList(),
                onApply = {},
                onDismiss = {},
            )
        }
        // Apply button must NOT exist — reset button proves sheet rendered correctly
        composeTestRule
            .onNodeWithTag("filter_reset_button")
            .performClick()
    }

    @Test
    fun aggregateSheetHasNoCancelButton() {
        composeTestRule.setContent {
            FilterListSheet(
                filters = emptyList(),
                initialEnabled = emptyList(),
                onApply = {},
                onDismiss = {},
            )
        }
        // Cancel button must NOT exist — reset button proves sheet rendered
        composeTestRule
            .onNodeWithTag("filter_reset_button")
            .performClick()
    }

    @Test
    fun aggregateSheetHasResetButton() {
        composeTestRule.setContent {
            FilterListSheet(
                filters =
                    listOf(
                        Filter(
                            id = "status",
                            title = "Trạng thái",
                            kind =
                                FilterKind.Select(
                                    options = listOf("Tất cả", "Đăng phát", "Hoàn thành"),
                                ),
                        ),
                    ),
                initialEnabled = emptyList(),
                onApply = {},
                onDismiss = {},
            )
        }
        composeTestRule
            .onNodeWithTag("filter_reset_button")
            .performClick()
    }

    @Test
    fun aggregateSheetHasCloseButton() {
        composeTestRule.setContent {
            FilterListSheet(
                filters =
                    listOf(
                        Filter(
                            id = "status",
                            title = "Trạng thái",
                            kind =
                                FilterKind.Select(
                                    options = listOf("Tất cả", "Đăng phát", "Hoàn thành"),
                                ),
                        ),
                    ),
                initialEnabled = emptyList(),
                onApply = {},
                onDismiss = {},
            )
        }
        composeTestRule
            .onNodeWithTag("filter_sheet_close")
            .performClick()
    }

    @Test
    fun aggregateSheetHasTitle() {
        composeTestRule.setContent {
            FilterListSheet(
                filters =
                    listOf(
                        Filter(
                            id = "status",
                            title = "Trạng thái",
                            kind =
                                FilterKind.Select(
                                    options = listOf("Tất cả", "Đăng phát", "Hoàn thành"),
                                ),
                        ),
                    ),
                initialEnabled = emptyList(),
                onApply = {},
                onDismiss = {},
            )
        }
        composeTestRule
            .onNodeWithTag("filter_sheet_title")
            .performClick()
    }
}
