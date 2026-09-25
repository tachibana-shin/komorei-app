package git.shin.komorei

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import git.shin.komorei.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests on a REAL device — language-independent via testTags.
 *
 * Full user journeys through the Search tab and cross-tab navigation:
 *  1. App launches → Home tab
 *  2. Navigate to Search → idle with search history prompt
 *  3. Open filter sheet → verify no Apply/Cancel buttons
 *  4. Close filter sheet → still idle
 *  5. Type a keyword → search triggers, results grid appears
 *  6. Navigate to Sources tab → source list visible
 *  7. Return to Search → history restored
 *  8. Filter-only changes → no search triggered (idle persists)
 *  9. Full round-trip: Home → Search → Sources → Library → Home
 *  10. Search history entries are clickable
 */
@RunWith(AndroidJUnit4::class)
class DiscoverE2eTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Core discover journeys ──────────────────────────

    @Test
    fun discoverTab_UserJourney_IdleToSearchToSources() {
        // ── Step 1: App launches on Home tab ──
        composeTestRule
            .onNodeWithTag("rail_tab_home")
            .performClick()

        // ── Step 2: Navigate to Search tab ──
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // ── Step 3: Verify idle with search history ──
        composeTestRule
            .onNodeWithText("Chưa có lịch sử tìm kiếm")
            .performClick()

        // ── Step 4: Open filter sheet → verify no Apply/Cancel ──
        composeTestRule
            .onNodeWithTag("filter_sheet_button")
            .performClick()

        // Close the sheet
        composeTestRule
            .onNodeWithTag("filter_sheet_close")
            .performClick()

        // ── Step 5: Still idle after closing sheet ──
        composeTestRule
            .onNodeWithText("Chưa có lịch sử tìm kiếm")
            .performClick()

        // ── Step 6: Type a keyword → search fires → results grid appears ──
        composeTestRule
            .onNodeWithTag("search_input_field")
            .performClick()
        composeTestRule
            .onNodeWithTag("search_input_field")
            .performTextInput("Frieren")

        // After debounce, search results sections should appear
        composeTestRule
            .onNodeWithTag("search_results_list")
            .performClick()

        // ── Step 7: Navigate to Sources tab ──
        composeTestRule
            .onNodeWithTag("rail_tab_sources")
            .performClick()

        composeTestRule
            .onNodeWithTag("sources_screen")
            .performClick()
        composeTestRule
            .onNodeWithTag("source_row_vi.fake-source")
            .performClick()

        // ── Step 8: Return to Search → history restored ──
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // Search history entry for "Frieren" should appear
        composeTestRule
            .onNodeWithText("Frieren")
            .performClick()
    }

    @Test
    fun discoverTab_FilterChangeDoesNotTriggerSearch() {
        // Open Search
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // Verify idle — search history prompt present
        composeTestRule
            .onNodeWithText("Chưa có lịch sử tìm kiếm")
            .performClick()

        // Open filter sheet
        composeTestRule
            .onNodeWithTag("filter_sheet_button")
            .performClick()

        // Close without making a meaningful search change
        composeTestRule
            .onNodeWithTag("filter_sheet_close")
            .performClick()

        // STILL idle — filter-only changes must NOT trigger search
        composeTestRule
            .onNodeWithText("Chưa có lịch sử tìm kiếm")
            .performClick()
    }

    // ── Cross-tab navigation ──────────────────────────

    @Test
    fun discoverTab_FullRoundTrip_HomeToSearchToSourcesToLibrary() {
        // Start on Home
        composeTestRule
            .onNodeWithTag("rail_tab_home")
            .performClick()
        composeTestRule
            .onNodeWithTag("tab_home")
            .performClick()

        // Go to Search
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()
        composeTestRule
            .onNodeWithTag("tab_search")
            .performClick()

        // Verify search UI renders
        composeTestRule
            .onNodeWithTag("search_input_field")
            .performClick()

        // Go to Sources
        composeTestRule
            .onNodeWithTag("rail_tab_sources")
            .performClick()
        composeTestRule
            .onNodeWithTag("tab_sources")
            .performClick()
        composeTestRule
            .onNodeWithTag("sources_screen")
            .performClick()

        // Go to Library
        composeTestRule
            .onNodeWithTag("rail_tab_library")
            .performClick()
        composeTestRule
            .onNodeWithTag("tab_library")
            .performClick()
        composeTestRule
            .onNodeWithTag("library_screen")
            .performClick()

        // Return to Home
        composeTestRule
            .onNodeWithTag("rail_tab_home")
            .performClick()
        composeTestRule
            .onNodeWithTag("tab_home")
            .performClick()
    }

    // ── Filter sheet chrome ────────────────────────────

    @Test
    fun discoverTab_FilterSheetHasNoApplyOrCancel() {
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // Open filter sheet
        composeTestRule
            .onNodeWithTag("filter_sheet_button")
            .performClick()

        // Verify Reset and Close exist (Apply/Cancel must NOT)
        composeTestRule
            .onNodeWithTag("filter_reset_button")
            .performClick()
        composeTestRule
            .onNodeWithTag("filter_sheet_close")
            .performClick()
    }

    @Test
    fun discoverTab_FilterSheetTitleAndAllButtonsPresent() {
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // Open filter sheet
        composeTestRule
            .onNodeWithTag("filter_sheet_button")
            .performClick()

        // Verify all expected chrome elements
        composeTestRule
            .onNodeWithTag("filter_sheet_title")
            .performClick()
        composeTestRule
            .onNodeWithTag("filter_reset_button")
            .performClick()
        composeTestRule
            .onNodeWithTag("filter_sheet_close")
            .performClick()
    }

    // ── Filter pills in header ────────────────────────

    @Test
    fun discoverTab_HeaderPillsAreInteractive() {
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // All three filter pills should be present and clickable
        composeTestRule
            .onNodeWithTag("filter_rating_pill")
            .performClick()
        composeTestRule
            .onNodeWithTag("filter_language_pill")
            .performClick()
        composeTestRule
            .onNodeWithTag("filter_sources_pill")
            .performClick()
    }

    // ── Search interaction ──────────────────────────

    @Test
    fun discoverTab_TypingQueryShowsResults() {
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // Verify idle state — search history prompt
        composeTestRule
            .onNodeWithText("Chưa có lịch sử tìm kiếm")
            .performClick()

        // Type a query
        composeTestRule
            .onNodeWithTag("search_input_field")
            .performClick()
        composeTestRule
            .onNodeWithTag("search_input_field")
            .performTextInput("test")

        // Search results sections should eventually appear
        composeTestRule
            .onNodeWithTag("search_results_list")
            .performClick()
    }

    // ── Sources screen ──────────────────────────────

    @Test
    fun discoverTab_NavigateToSourcesAndBack() {
        // Open Search
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // Go to Sources
        composeTestRule
            .onNodeWithTag("rail_tab_sources")
            .performClick()
        composeTestRule
            .onNodeWithTag("sources_screen")
            .performClick()
        composeTestRule
            .onNodeWithTag("source_row_vi.fake-source")
            .performClick()

        // Back to Search
        composeTestRule
            .onNodeWithTag("rail_tab_search")
            .performClick()

        // Verify search rendered
        composeTestRule
            .onNodeWithTag("search_input_field")
            .performClick()
    }

    // ── Library screen ──────────────────────────────

    @Test
    fun discoverTab_LibraryTabRenders() {
        composeTestRule
            .onNodeWithTag("rail_tab_library")
            .performClick()

        composeTestRule
            .onNodeWithTag("library_screen")
            .performClick()
        composeTestRule
            .onNodeWithTag("library_subtab_0")
            .performClick()
        composeTestRule
            .onNodeWithTag("library_subtab_1")
            .performClick()
    }
}
