package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.model.FilterKind
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

/**
 * The single-select picker (Aidoku `SelectFilterGroupView`): a wrapping row
 * of tag chips when `usesTagStyle` is set, otherwise a vertical list of rows
 * with a checkmark on the selected option. Option values resolve through the
 * filter's `ids` array (falling back to the option text itself).
 *
 * A controlled component — the parent owns the selection.
 */
@Composable
fun SelectFilterGroup(
    kind: FilterKind.Select,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = kind.options.mapIndexed { index, option -> kind.ids?.getOrNull(index) ?: option }
    if (kind.usesTagStyle) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            kind.options.forEachIndexed { index, option ->
                val value = values[index]
                val selectedOption = value == selected
                val shape = RoundedCornerShape(100)
                Text(
                    text = option,
                    color = if (selectedOption) Color.White else TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        // TV focus highlight (no-op on phones).
                        .tvFocus(shape = shape, scale = 1.06f)
                        .clip(shape)
                        .background(if (selectedOption) AnimeRed else SurfaceDark)
                        .border(
                            width = 1.dp,
                            color = if (selectedOption) AnimeRed else CardBorderDark,
                            shape = shape,
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            kind.options.forEachIndexed { index, option ->
                val value = values[index]
                val selectedOption = value == selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        // TV focus highlight (no-op on phones) — full-width row, ring only.
                        .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.0f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = option,
                        color = if (selectedOption) AnimeRed else TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (selectedOption) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (selectedOption) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = AnimeRed,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}