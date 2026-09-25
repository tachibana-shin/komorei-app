package git.shin.komorei

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue
import git.shin.komorei.ui.components.search.filters.FilterHeaderRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [FilterHeaderRow] — verifies the applied-first pill
 * ordering: red (applied) pills sort to the front, the most-selected furthest
 * front, while inactive pills keep the source's original filter order.
 *
 * All lookups use testTags (language-independent).
 */
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class FilterHeaderRowTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val sort =
        Filter(
            id = "sort",
            title = "Sort",
            kind = FilterKind.Sort(options = listOf("New")),
        )
    private val genre =
        Filter(
            id = "genre",
            title = "Genre",
            kind = FilterKind.Select(options = listOf("A", "B"), usesTagStyle = true),
        )
    private val country =
        Filter(
            id = "country",
            title = "Country",
            kind = FilterKind.MultiSelect(options = listOf("VN", "US", "JP"), usesTagStyle = true),
        )

    private fun setRow(enabled: List<FilterValue>) {
        composeTestRule.setContent {
            FilterHeaderRow(
                filters = listOf(sort, genre, country),
                enabledFilters = enabled,
                onFilterValueChange = { _, _ -> },
                onOpenFilterSheet = {},
            )
        }
    }

    private fun leftOf(tag: String): Float =
        composeTestRule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .boundsInRoot.left

    @Test
    fun appliedPillsSortToFrontBySelectedCount() {
        setRow(
            enabled =
                listOf(
                    FilterValue.Select("genre", "A"),
                    FilterValue.MultiSelect("country", included = listOf("VN", "US", "JP"), excluded = listOf("SG")),
                ),
        )
        // country (4 selected) before genre (1), both before the inactive sort
        assertTrue(leftOf("filter_pill_Country") < leftOf("filter_pill_Genre"))
        assertTrue(leftOf("filter_pill_Genre") < leftOf("filter_pill_Sort: New"))
    }

    @Test
    fun inactivePillsKeepSourceOrder() {
        setRow(enabled = emptyList())
        assertTrue(leftOf("filter_pill_Sort: New") < leftOf("filter_pill_Genre"))
        assertTrue(leftOf("filter_pill_Genre") < leftOf("filter_pill_Country"))
    }
}
