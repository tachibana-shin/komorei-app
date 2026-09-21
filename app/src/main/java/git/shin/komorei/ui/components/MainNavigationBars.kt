package git.shin.komorei.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.ui.navigation.MainTab
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted

/**
 * Phone bottom navigation.
 *
 * The app now has more than the 5 destinations a Material [androidx.compose.material3.NavigationBar]
 * comfortably fits, so this is a horizontally scrollable strip of compact items.
 * It auto-scrolls the selected tab into view (a tab selected from elsewhere —
 * deep link, back stack — is always visible) and keeps the same tinting/testTags
 * the old fixed bar used.
 */
@Composable
fun MainBottomNavigation(
    tabs: List<MainTab>,
    currentRoute: String,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = remember(tabs, currentRoute) {
        tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("main_bottom_navigation"),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items = tabs, key = { it.key }) { tab ->
            BottomNavItem(
                tab = tab,
                selected = tab.route == currentRoute,
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .width(74.dp)
                    .testTag("tab_${tab.key}"),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .width(56.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) AnimeRed else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = stringResource(tab.labelRes),
                tint = if (selected) Color.White else TextMuted,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(tab.labelRes),
            color = if (selected) AnimeRed else TextMuted,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * Tablet / landscape navigation rail. Vertical and scrollable for the same
 * reason the bottom bar is horizontal: there are more destinations than fit at
 * once on a short (landscape phone) viewport.
 */
@Composable
fun MainNavigationRail(
    tabs: List<MainTab>,
    currentRoute: String,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedIndex = remember(tabs, currentRoute) {
        tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(SurfaceDark)
            .windowInsetsPadding(WindowInsets.systemBars)
            .testTag("main_navigation_rail"),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items = tabs, key = { it.key }) { tab ->
            RailNavItem(
                tab = tab,
                selected = tab.route == currentRoute,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag("rail_tab_${tab.key}"),
            )
        }
    }
}

@Composable
private fun RailNavItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .width(56.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) AnimeRed else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = stringResource(tab.labelRes),
                tint = if (selected) Color.White else TextMuted,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(tab.labelRes),
            color = if (selected) AnimeRed else TextMuted,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
