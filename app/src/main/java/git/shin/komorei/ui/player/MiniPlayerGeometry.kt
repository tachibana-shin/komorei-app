package git.shin.komorei.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Shared geometry of the collapsed mini bubble: its 16:9 size (px), the four screen
 * corner anchors, and the "appear" start point the bubble glides away from during the
 * collapse handoff. Used by BOTH [FloatingMiniPlayer] (sizing, initial position, corner
 * snapping, drag bounds) and [VideoPlayerSheet] (where the sheet's video slides out and
 * the direction the bubble comes from) so the player→mini-player handoff reads as one
 * continuous motion instead of a hard pop.
 *
 * Bottom corners are raised above `bottomInsetPx` (the app's bottom navigation bar) so
 * the mini player never covers the toolbar — "chừa bottom toolbar ra".
 */
data class MiniPlayerGeometry(
    val width: Float,
    val height: Float,
    val cornerTopLeft: Offset,
    val cornerTopRight: Offset,
    val cornerBottomLeft: Offset,
    val cornerBottomRight: Offset,
    /** Safe-area clearance below the bottom corners (the app's bottom toolbar, px). */
    val bottomInsetPx: Float,
) {
    /**
     * Where the bubble appears right after the sheet video exits the bottom of the
     * screen: horizontally centered, parked just above the bottom toolbar — the video's
     * last visible region. The bubble then springs to its remembered corner, which is
     * what makes the collapse feel like "player và mini player là một".
     */
    val bottomCenterStart: Offset
        get() =
            Offset(
                (cornerBottomLeft.x + cornerBottomRight.x) / 2f,
                cornerBottomLeft.y,
            )

    /** The corner anchor whose CENTER is closest to the bubble's [topLeft] corner. */
    fun nearestCorner(topLeft: Offset): Offset {
        val cx = topLeft.x + width / 2f
        val cy = topLeft.y + height / 2f
        val anchors =
            listOf(
                cornerTopLeft to Offset(cornerTopLeft.x + width / 2f, cornerTopLeft.y + height / 2f),
                cornerTopRight to Offset(cornerTopRight.x + width / 2f, cornerTopRight.y + height / 2f),
                cornerBottomLeft to Offset(cornerBottomLeft.x + width / 2f, cornerBottomLeft.y + height / 2f),
                cornerBottomRight to Offset(cornerBottomRight.x + width / 2f, cornerBottomRight.y + height / 2f),
            )
        var best = cornerBottomRight
        var bestDist = Float.MAX_VALUE
        for ((anchor, center) in anchors) {
            val dx = center.x - cx
            val dy = center.y - cy
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = anchor
            }
        }
        return best
    }
}

/** Bubble width = half the overlay width, clamped to a comfortable phone range. */
fun computeMiniPlayerGeometry(
    maxWidthPx: Float,
    maxHeightPx: Float,
    density: Density,
    bottomInsetPx: Float = 0f,
): MiniPlayerGeometry {
    val marginPx = with(density) { 12.dp.toPx() }
    val minWidthPx = with(density) { 150.dp.toPx() }
    val maxWidthPxCap = with(density) { 300.dp.toPx() }
    val width =
        (maxWidthPx * 0.5f)
            .coerceIn(minWidthPx, maxWidthPxCap)
            .coerceAtMost(maxWidthPx - marginPx * 2f)
    val height = width * 9f / 16f
    // Bottom corners sit above the toolbar (bottomInsetPx) plus the usual edge margin.
    val bottomMarginPx = marginPx + bottomInsetPx
    return MiniPlayerGeometry(
        width = width,
        height = height,
        cornerTopLeft = Offset(marginPx, marginPx),
        cornerTopRight = Offset(maxWidthPx - width - marginPx, marginPx),
        cornerBottomLeft = Offset(marginPx, maxHeightPx - height - bottomMarginPx),
        cornerBottomRight = Offset(maxWidthPx - width - marginPx, maxHeightPx - height - bottomMarginPx),
        bottomInsetPx = bottomInsetPx,
    )
}
