package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import git.shin.komorei.model.FilterKind
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

/**
 * The sort picker (Aidoku `SortFilterGroupView`): a wrapping row of option
 * chips. The selected chip is accented and, when the filter `canAscend`,
 * toggles ascending order on each tap (arrows ⇅ on the chip).
 *
 * A controlled component — the parent owns the selection and commits it
 * (the pill commits immediately, the full filter sheet commits into its
 * draft on "Áp dụng").
 */
@Composable
fun SortFilterGroup(
    kind: FilterKind.Sort,
    selectedIndex: Int,
    ascending: Boolean,
    onOptionChange: (index: Int, ascending: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        kind.options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(100)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier
                        // TV focus highlight (no-op on phones).
                        .tvFocus(shape = shape, scale = 1.06f)
                        .clip(shape)
                        .background(if (selected) AnimeRed else SurfaceDark)
                        .border(
                            width = 1.dp,
                            color = if (selected) AnimeRed else CardBorderDark,
                            shape = shape,
                        ).clickable {
                            if (selected && kind.canAscend) {
                                onOptionChange(index, !ascending)
                            } else {
                                onOptionChange(index, false)
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = option,
                    color = if (selected) Color.White else TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (selected && kind.canAscend) {
                    Icon(
                        imageVector =
                            if (ascending) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                        contentDescription = stringResource(if (ascending) R.string.filter_sort_asc_cd else R.string.filter_sort_desc_cd),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
