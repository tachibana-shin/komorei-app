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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.model.FilterKind
import git.shin.komorei.ui.theme.AnimeGreen
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

/**
 * The multi-select picker (Aidoku `MultiSelectFilterGroupView`): tapping an
 * option cycles included → (excluded when `canExclude`) → unset, driven by
 * [toggleMultiSelectOption]. Rendered as a wrapping row of tag chips
 * (`usesTagStyle`, e.g. the fake source's "Thể loại" pills) or as a vertical
 * list of rows with ✓ / ✗ marks.
 *
 * A controlled component — the parent owns the included/excluded lists.
 */
@Composable
fun MultiSelectFilterGroup(
    kind: FilterKind.MultiSelect,
    included: List<String>,
    excluded: List<String>,
    onToggle: (included: List<String>, excluded: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = kind.options.mapIndexed { index, option -> kind.ids?.getOrNull(index) ?: option }
    val toggle = { option: String ->
        val (nextIncluded, nextExcluded) = toggleMultiSelectOption(included, excluded, kind.canExclude, option)
        onToggle(nextIncluded, nextExcluded)
    }

    if (kind.usesTagStyle) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            kind.options.forEachIndexed { index, option ->
                val value = values[index]
                val isIncluded = value in included
                val isExcluded = value in excluded
                val shape = RoundedCornerShape(100)
                Text(
                    text = option,
                    color = if (isIncluded || isExcluded) Color.White else TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier =
                        Modifier
                            // TV focus highlight (no-op on phones).
                            .tvFocus(shape = shape, scale = 1.06f)
                            .clip(shape)
                            .background(
                                when {
                                    isIncluded -> AnimeRed
                                    isExcluded -> AnimeGreen
                                    else -> SurfaceDark
                                },
                            ).border(
                                width = 1.dp,
                                color = if (isIncluded || isExcluded) Color.Transparent else CardBorderDark,
                                shape = shape,
                            ).clickable { toggle(value) }
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
                val isIncluded = value in included
                val isExcluded = value in excluded
                val stateColor =
                    when {
                        isIncluded -> AnimeRed
                        isExcluded -> AnimeGreen
                        else -> SurfaceDark
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // TV focus highlight (no-op on phones) — full-width row, ring only.
                            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.0f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { toggle(value) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier =
                            Modifier
                                .size(width = 26.dp, height = 26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(stateColor)
                                .border(
                                    width = 1.dp,
                                    color = if (isIncluded || isExcluded) Color.Transparent else CardBorderDark,
                                    shape = RoundedCornerShape(7.dp),
                                ),
                    ) {
                        if (isIncluded || isExcluded) {
                            Icon(
                                imageVector = if (isIncluded) Icons.Default.Check else Icons.Default.Close,
                                contentDescription =
                                    stringResource(
                                        if (isIncluded) R.string.filter_included_cd else R.string.filter_excluded_cd,
                                    ),
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = option,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
