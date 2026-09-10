package git.shin.komorei.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.model.Source
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

@Composable
fun SourceTabBar(
    sources: List<Source>,
    selectedIndex: Int,
    onTabSelected: (Int, Source) -> Unit,
    modifier: Modifier = Modifier
) {
    SecondaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (sources.size - 1).coerceAtLeast(0)),
        containerColor = BackgroundDark,
        contentColor = TextPrimary,
        edgePadding = 16.dp,
        divider = {},
        indicator = {
            Box(
                modifier = Modifier
                    .tabIndicatorOffset(selectedTabIndex = selectedIndex, matchContentSize = true)
                    .height(3.dp)
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AnimeRed)
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("source_tab_row")
    ) {
        sources.forEachIndexed { index, source ->
            val isSelected = selectedIndex == index

            Tab(
                selected = isSelected,
                onClick = { onTabSelected(index, source) },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .testTag("source_tab_${source.id}"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.getSourceIcon(source.id),
                            contentDescription = source.name,
                            tint = if (isSelected) AnimeRed else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = source.name,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AnimeRed else TextMuted
                        )
                    }
                }
            )
        }
    }
}
