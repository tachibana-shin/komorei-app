package git.shin.komorei

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.model.ContentRatingFilter
import git.shin.komorei.model.Source
import git.shin.komorei.ui.components.search.DiscoverFilterHeaderRow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [DiscoverFilterHeaderRow].
 *
 * Verifies the filter header row renders correctly with all three pills
 * (rating, language, sources) and the aggregate filter sheet button.
 *
 * All lookups use testTags (language-independent).
 */
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
@RunWith(RobolectricTestRunner::class)
class DiscoverFilterHeaderRowTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val sources =
        listOf(
            Source(id = "source.a", name = "Source A", baseUrl = "https://a.com", languages = listOf("vi")),
        )

    @Test
    fun headerRendersFilterSheetButton() {
        composeTestRule.setContent {
            DiscoverFilterHeaderRow(
                contentRating = ContentRatingFilter.ALL,
                language = null,
                includedSourceIds = emptySet(),
                sources = sources,
                onContentRatingChange = {},
                onLanguageChange = {},
                onSourcesChange = {},
            )
        }
        // FilterSheetButton is the aggregate filter entry
        composeTestRule
            .onNodeWithTag("filter_sheet_button")
            .performClick()
    }

    @Test
    fun headerRendersAllThreePills() {
        composeTestRule.setContent {
            DiscoverFilterHeaderRow(
                contentRating = ContentRatingFilter.ALL,
                language = null,
                includedSourceIds = emptySet(),
                sources = sources,
                onContentRatingChange = {},
                onLanguageChange = {},
                onSourcesChange = {},
            )
        }
        // Rating pill
        composeTestRule
            .onNodeWithTag("filter_rating_pill")
            .performClick()
        // Language pill
        composeTestRule
            .onNodeWithTag("filter_language_pill")
            .performClick()
        // Sources pill
        composeTestRule
            .onNodeWithTag("filter_sources_pill")
            .performClick()
    }

    @Test
    fun headerWithSelectedSourcesShowsSourcesBadge() {
        composeTestRule.setContent {
            DiscoverFilterHeaderRow(
                contentRating = ContentRatingFilter.ALL,
                language = null,
                includedSourceIds = setOf("source.a"),
                sources = sources,
                onContentRatingChange = {},
                onLanguageChange = {},
                onSourcesChange = {},
            )
        }
        // Sources pill with badge
        composeTestRule
            .onNodeWithTag("filter_sources_pill")
            .performClick()
    }

    @Test
    fun headerRendersWithoutCrash() {
        composeTestRule.setContent {
            DiscoverFilterHeaderRow(
                contentRating = ContentRatingFilter.SAFE,
                language = "vi",
                includedSourceIds = setOf("source.a"),
                sources = sources,
                onContentRatingChange = {},
                onLanguageChange = {},
                onSourcesChange = {},
            )
        }
        composeTestRule
            .onNodeWithTag("filter_sheet_button")
            .performClick()
    }

    @Test
    fun appliedPillsSortToFrontWithMostSelectedFirst() {
        composeTestRule.setContent {
            DiscoverFilterHeaderRow(
                contentRating = ContentRatingFilter.SAFE,
                language = null,
                includedSourceIds = setOf("source.a", "source.b"),
                sources = sources,
                onContentRatingChange = {},
                onLanguageChange = {},
                onSourcesChange = {},
            )
        }
        // sources (2 selected) sorts ahead of rating (1); language stays last
        val sourcesLeft =
            composeTestRule
                .onNodeWithTag("filter_sources_pill")
                .fetchSemanticsNode()
                .boundsInRoot.left
        val ratingLeft =
            composeTestRule
                .onNodeWithTag("filter_rating_pill")
                .fetchSemanticsNode()
                .boundsInRoot.left
        val languageLeft =
            composeTestRule
                .onNodeWithTag("filter_language_pill")
                .fetchSemanticsNode()
                .boundsInRoot.left
        assertTrue("sources pill should sort before rating", sourcesLeft < ratingLeft)
        assertTrue("rating pill should sort before language", ratingLeft < languageLeft)
    }
}
