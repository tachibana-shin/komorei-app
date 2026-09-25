package git.shin.komorei.ui.components.home

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Upper bound for a paged cell's fixed width on the PHONE's 2-column layout
 * (≈67% of a phone's content width). Shared by every `page_size` paged grid —
 * [AnimeListRow] and [AnimeEpisodeListRow] both render their pages with
 * [pagedCellWidth], so the cells stay **packed** on phones (two columns,
 * never a percentage/weight) and identical across home sections.
 */
internal val PAGED_CELL_WIDTH_MAX = 180.dp

/**
 * How many columns a paged home grid renders, per width bucket: 2 on phones,
 * 3 on tablets, 4 on very large screens (TVs). The page rows chunk their
 * items into this many columns so the section reads as a proper grid instead
 * of two over-wide rows on a tablet.
 */
@Composable
internal fun BoxWithConstraintsScope.pagedGridColumns(): Int =
    when {
        maxWidth < 600.dp -> 2
        maxWidth < 1200.dp -> 3
        else -> 4
    }

/**
 * Fixed width of one paged cell: the measured container divided by
 * [pagedGridColumns] (16dp page padding each side + 12dp inter-column
 * spacing). On the phone's 2-column layout the width keeps the classic
 * [PAGED_CELL_WIDTH_MAX] cap so cards never balloon; on tablet/TV layouts the
 * cells fill their whole budget so the extra columns stay packed with no
 * leftover gap. Never weight/percentage — the width feel of Aidoku's
 * `mangaListLayout` page fraction with a fixed dp.
 */
@Composable
internal fun BoxWithConstraintsScope.pagedCellWidth(
    columns: Int = pagedGridColumns(),
): Dp {
    val budget = (maxWidth - 32.dp - 12.dp * (columns - 1)) / columns
    return if (columns == 2) {
        budget.coerceAtMost(PAGED_CELL_WIDTH_MAX)
    } else {
        budget
    }
}
