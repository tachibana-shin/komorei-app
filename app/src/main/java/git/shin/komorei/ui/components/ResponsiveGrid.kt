package git.shin.komorei.ui.components

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
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
