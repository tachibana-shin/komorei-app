package git.shin.komorei.ui.components.home

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Upper bound for a paged cell's fixed width (≈67% of a phone's content width).
 * Shared by every `page_size` paged grid — [AnimeListRow] and
 * [AnimeEpisodeListRow] both render their pages with [pagedCellWidth], so the
 * cells stay **packed** (two columns, never a percentage/weight) and identical
 * across home sections.
 */
internal val PAGED_CELL_WIDTH_MAX = 180.dp

/**
 * Fixed width of one paged cell: half of the measured container (16dp page
 * padding each side + 12dp inter-column spacing), capped at
 * [PAGED_CELL_WIDTH_MAX]. Never weight/percentage — mirrors the width feel of
 * Aidoku's `mangaListLayout` page fraction while keeping a fixed dp.
 */
@Composable
internal fun BoxWithConstraintsScope.pagedCellWidth(): Dp =
    ((maxWidth - 32.dp - 12.dp) / 2).coerceAtMost(PAGED_CELL_WIDTH_MAX)