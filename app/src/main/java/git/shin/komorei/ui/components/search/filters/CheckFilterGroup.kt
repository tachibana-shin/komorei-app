package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextPrimary

/**
 * The tristate checkbox picker (Aidoku `CheckFilterGroupView`): a checkbox
 * cycling 0 → 1 (✓ on) → 2 (✗ excluded, when `canExclude`) → 0. Used inside
 * the full filter sheet — the header's `CheckFilterPill` is the same widget
 * wired as an inline button (Aidoku parity).
 *
 * A controlled component — the parent owns the tristate [state].
 */
@Composable
fun CheckFilterGroup(
    filter: Filter,
    state: Int,
    onStateChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = (filter.kind as? FilterKind.Check) ?: return
    val name = kind.name ?: filter.title.orEmpty()
    val shape = RoundedCornerShape(7.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onStateChange(nextCheckState(state, kind.canExclude)) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(width = 26.dp, height = 26.dp)
                .clip(shape)
                .background(if (state != 0) AnimeRed else SurfaceDark)
                .border(
                    width = 1.dp,
                    color = if (state != 0) Color.Transparent else CardBorderDark,
                    shape = shape,
                ),
        ) {
            if (state != 0) {
                Icon(
                    imageVector = if (state == 1) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = stringResource(
                        when (state) {
                            1 -> R.string.filter_check_on_cd
                            2 -> R.string.filter_check_excluded_cd
                            else -> R.string.filter_check_off_cd
                        }
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = name,
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (state != 0) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Cycles the tristate check index (Aidoku `CheckFilterView`'s button logic). */
fun nextCheckState(state: Int, canExclude: Boolean): Int = when (state) {
    0 -> 1
    1 -> if (canExclude) 2 else 0
    else -> 0
}