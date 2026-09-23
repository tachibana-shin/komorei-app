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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextPrimary

/**
 * The shared pill chrome of Aidoku's `FilterLabelView`: an optional count
 * badge, the filter name and a chevron. Inactive pills are dimmed (name at
 * 60% — Aidoku `highlighted ? 1 : 0.6`); active pills are tinted with the
 * accent color. NOT a Material `Surface(onClick)` — the interactive-component
 * floor would pin the height to 48dp (same reason as the listing chips).
 */
@Composable
fun FilterPill(
    name: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    chevron: Boolean = true,
    icon: ImageVector? = null,
    testTag: String = "",
) {
    val shape = RoundedCornerShape(100)
    val borderColor = if (active) AnimeRed.copy(alpha = 0.5f) else CardBorderDark
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .testTag(if (testTag.isNotEmpty()) testTag else "filter_pill_$name")
            .clip(shape)
            .background(if (active) AnimeRed.copy(alpha = 0.16f) else SurfaceDark)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        // Always render the badge (even when count <= 1) so the Row's
        // width doesn't change when data loads — prevents layout shift.
        FilterBadge(count = badgeCount)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) AnimeRed else TextPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = name,
            color = if (active) AnimeRed else TextPrimary.copy(alpha = 0.6f),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (chevron) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (active) AnimeRed else TextPrimary.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * A small circular count badge (Aidoku `FilterBadgeView`).
 *
 * Sized like a Material badge (18dp disc) so it sits beside the pill's 16dp
 * icon without dwarfing it — the old fixed 32dp disc stuck out of the pill
 * row and visually overflowed into the content below.
 */
@Composable
fun FilterBadge(count: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(100)
    val isVisible = count > 0
    Text(
        text = count.toString(),
        color = Color.White,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .size(if (isVisible) 18.dp else 1.dp)
            .clip(shape)
            .background(if (isVisible) AnimeRed else Color.Transparent)
            .alpha(if (isVisible) 1f else 0f)
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}