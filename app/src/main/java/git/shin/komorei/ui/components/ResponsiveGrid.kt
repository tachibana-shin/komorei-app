package git.shin.komorei.ui.components

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Maximum width of the main content column on very large screens (landscape
 * tablets, Android TV). The shell centers the NavHost content inside this
 * cap so lists and grids never stretch edge-to-edge on a 1080p+ display —
 * beyond it the root background ([git.shin.komorei.ui.theme.BackgroundDark])
 * simply shows through on both sides. Phones, portrait tablets and the
 * navigation-rail layout are all below the cap and are untouched.
 */
val MAX_CONTENT_WIDTH = 1200.dp

/**
 * Column count for the app's anime grids (listing screen, per-source search,
 * home listing page and the library grid), tuned per width bucket.
 *
 * Phones (≤480dp of content) keep the classic 3 columns; wide screens spread
 * to 4–6 columns so cards stay a sane size instead of ballooning to fill a
 * tablet or TV. Measured from the available content width (after the
 * navigation rail / [MAX_CONTENT_WIDTH] cap), so every host stays consistent.
 */
@Composable
fun BoxWithConstraintsScope.animeGridColumnCount(): Int =
    when {
        maxWidth < 480.dp -> 3
        maxWidth < 720.dp -> 4
        maxWidth < 1024.dp -> 5
        else -> 6
    }

/** [animeGridColumnCount] as [GridCells] — the default for every anime grid. */
@Composable
fun BoxWithConstraintsScope.animeGridColumns(): GridCells = GridCells.Fixed(animeGridColumnCount())

/** Page padding a hand-rolled grid row carries on each side. */
val PAGE_PADDING_HORIZONTAL = 16.dp

/** Spacing between two cells in a row. */
val GRID_GUTTER = 12.dp

/** Vertical gap between two rows of a hand-rolled grid. */
val GRID_ROW_SPACING = 14.dp

/**
 * Width one column of a [columns]-wide grid may occupy, given the width of the
 * container the grid was measured in.
 *
 * The padding is the easy part to get wrong, and it fails quietly. A grid that
 * pads its rows *inside* the container it measured has to subtract that padding
 * here, or the row asks for more width than it has — and Compose answers that by
 * squeezing the last cell to fit rather than by complaining. On a 360dp phone
 * the detail page's Related grid asked for 360dp of cells inside a 328dp row, so
 * its third card rendered 64dp narrower than the two beside it, with no warning
 * anywhere.
 *
 * The `LazyVerticalGrid` callers do not need this: `contentPadding` is
 * subtracted by the layout itself. It is for the grids built by hand out of
 * `Row`s — the home paged grids and this one.
 *
 * The result always divides the row exactly: `columns` cells and `columns - 1`
 * gutters fill the padded width with nothing left over and nothing over, which is
 * what keeps every cell in a row the same width.
 */
fun gridCellWidthFor(
    containerWidth: Dp,
    columns: Int,
): Dp = (containerWidth - PAGE_PADDING_HORIZONTAL * 2 - GRID_GUTTER * (columns - 1)) / columns

/** [gridCellWidthFor] against this container. */
@Composable
fun BoxWithConstraintsScope.gridCellWidth(columns: Int): Dp = gridCellWidthFor(maxWidth, columns)
