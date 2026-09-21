package git.shin.komorei.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import git.shin.komorei.R

/**
 * One destination in the app shell's navigation (bottom bar on phones, rail on
 * tablets). The bars are data-driven off [mainTabs] so adding a tab means adding
 * a [Screen] + one entry here (instead of duplicating a hard-coded item block in
 * both the rail and the bottom bar).
 */
data class MainTab(
    /** Stable short key used for testTags (`tab_<key>` / `rail_tab_<key>`). */
    val key: String,
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/** Every top-level destination, in bottom-bar / rail order. */
val mainTabs: List<MainTab> = listOf(
    MainTab(
        key = "home",
        route = Screen.Home.route,
        labelRes = R.string.tab_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    MainTab(
        key = "search",
        route = Screen.Search.route,
        labelRes = R.string.tab_search,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
    ),
    MainTab(
        key = "schedule",
        route = Screen.Schedule.route,
        labelRes = R.string.tab_schedule,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
    ),
    MainTab(
        key = "rankings",
        route = Screen.Rankings.route,
        labelRes = R.string.tab_rankings,
        selectedIcon = Icons.Filled.Leaderboard,
        unselectedIcon = Icons.Outlined.Leaderboard,
    ),
    MainTab(
        key = "notifications",
        route = Screen.Notifications.route,
        labelRes = R.string.tab_notifications,
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.Notifications,
    ),
    MainTab(
        key = "sources",
        route = Screen.Sources.route,
        labelRes = R.string.tab_sources,
        selectedIcon = Icons.Filled.Language,
        unselectedIcon = Icons.Outlined.Language,
    ),
    MainTab(
        key = "library",
        route = Screen.Library.route,
        labelRes = R.string.tab_library,
        selectedIcon = Icons.Filled.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
    ),
    MainTab(
        key = "settings",
        route = Screen.Settings.route,
        labelRes = R.string.tab_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)
