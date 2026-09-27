package git.shin.komorei

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import git.shin.komorei.ui.components.GRID_GUTTER
import git.shin.komorei.ui.components.PAGE_PADDING_HORIZONTAL
import git.shin.komorei.ui.components.gridCellWidthFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The width arithmetic every hand-rolled anime grid depends on.
 *
 * A `Row` of fixed-width cards does not complain when the widths it is given add
 * up to more than the row has. Compose measures the children that fit and
 * squeezes the rest, so the row comes out looking *almost* right — one card
 * narrower than its neighbours, no error, no log. On the detail page's Related
 * grid that made the third card 64dp narrower than the two beside it on a 360dp
 * phone, which is the only reason anyone noticed.
 *
 * These assertions are about the whole row, not one cell: the total the grid asks
 * for has to equal the width it has, for every column count and every width
 * bucket the app supports.
 */
class GridCellWidthTest {
    /**
     * Dp division is floating point, so comparisons go through the raw value.
     * A thousandth of a dp is far below a pixel — tight enough to catch a
     * padding mistake, loose enough not to fail on rounding.
     */
    private val tolerance = 0.001f

    /** The width a row actually has once the page padding comes off. */
    private fun rowWidth(containerWidth: Dp) = containerWidth - PAGE_PADDING_HORIZONTAL * 2

    private fun rowAsks(
        containerWidth: Dp,
        columns: Int,
    ): Dp = gridCellWidthFor(containerWidth, columns) * columns + GRID_GUTTER * (columns - 1)

    private fun assertDpEquals(
        expected: Dp,
        actual: Dp,
        message: String,
    ) = assertEquals(message, expected.value, actual.value, tolerance)

    @Test
    fun `a row of cells asks for exactly the width it has`() {
        // Every width bucket animeGridColumnCount returns a column count for, at
        // the column counts it can pick.
        val widths =
            listOf(
                280.dp, // small phone
                320.dp,
                360.dp, // the phone that showed the bug
                411.dp,
                480.dp, // 3 -> 4 columns
                600.dp,
                720.dp, // 4 -> 5 columns
                840.dp,
                1024.dp, // 5 -> 6 columns
                1200.dp,
            )
        for (width in widths) {
            for (columns in 1..6) {
                assertDpEquals(
                    rowWidth(width),
                    rowAsks(width, columns),
                    "columns=$columns at $width ask for more or less than the row has",
                )
            }
        }
    }

    @Test
    fun `the padding is what makes a row fit`() {
        // The specific arithmetic that was wrong: measuring the cells against the
        // container instead of the row inside it.
        val container = 360.dp
        val columns = 3
        val correct = gridCellWidthFor(container, columns)
        val withoutPadding = (container - GRID_GUTTER * (columns - 1)) / columns

        assertTrue(
            "the fixed width must be narrower than the un-padded version",
            correct.value < withoutPadding.value,
        )
        // 360 - 32 = 328 available; three cells plus two 12dp gutters must total
        // 328, not 360.
        assertDpEquals(rowWidth(container), rowAsks(container, columns), "row does not fill its width")
    }
}
