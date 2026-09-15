package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark

/**
 * The aggregate-filter entry of the filter header (Aidoku `FilterHeaderView`'s
 * filter sheet button): an icon pill that opens the bottom sheet listing ALL
 * of the source's filters. Shows the running [activeFilterCount] badge.
 */
@Composable
fun FilterSheetButton(
    enabledCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(100)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(shape)
            .background(SurfaceDark)
            .border(width = 1.dp, color = if (enabledCount > 0) AnimeRed.copy(alpha = 0.5f) else CardBorderDark, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = stringResource(R.string.filter_sheet_cd),
            tint = if (enabledCount > 0) AnimeRed else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        if (enabledCount > 0) {
            FilterBadge(count = enabledCount)
        }
    }
}