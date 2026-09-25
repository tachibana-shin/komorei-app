package git.shin.komorei.ui.utils

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

/**
 * Scrolls a vertical [LazyListState] (e.g. episode list) so the item at [targetIndex]
 * becomes visible at the start edge. No-op when the item is already fully visible.
 */
suspend fun LazyListState.scrollToItemVisible(targetIndex: Int) {
    if (targetIndex < 0) return
    if (layoutInfo.visibleItemsInfo.any { it.index == targetIndex }) return
    animateScrollToItem(targetIndex)
}

/**
 * Centers the item at [targetIndex] inside a horizontal [LazyListState]
 * (e.g. season / episode chip rows). Falls back to a plain scroll when the
 * layout is not measured yet.
 *
 * The centering step scrolls by the exact pixel delta between the item's current
 * offset and the centered offset — a pure incremental `animateScrollBy(delta)`.
 * Never `animateScrollToItem(index, firstVisibleItemScrollOffset + delta)`: that
 * feeds an absolute scroll position into the per-item `scrollOffset` parameter,
 * which double-counts the existing scroll (~2× overshoot — the "scroll value is
 * added to the old scrollX twice" symptom).
 */
suspend fun LazyListState.animateScrollToItemCentered(targetIndex: Int) {
    if (targetIndex < 0) return

    if (layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
        animateScrollToItem(targetIndex)
    }

    val layout = layoutInfo
    val item = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex } ?: return
    val viewportSize = layout.viewportEndOffset - layout.viewportStartOffset
    if (viewportSize <= 0) return

    val desiredStart = (viewportSize - item.size) / 2
    val delta = item.offset - desiredStart
    if (delta == 0) return

    animateScrollBy(delta.toFloat())
}

/**
 * Scrolls a [LazyGridState] (episode grid) so the row containing [targetIndex] becomes visible.
 */
suspend fun LazyGridState.scrollToItemVisible(targetIndex: Int) {
    if (targetIndex < 0) return
    if (layoutInfo.visibleItemsInfo.any { it.index == targetIndex }) return
    animateScrollToItem(targetIndex)
}
