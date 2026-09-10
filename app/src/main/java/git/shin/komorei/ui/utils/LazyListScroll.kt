package git.shin.komorei.ui.utils

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
 * Mapping used: `scrollToItem(index, scrollOffset)` positions the item so its start edge
 * sits `-scrollOffset` px relative to the viewport start (positive scrollOffset = item
 * scrolled upward / offscreen). Since scrolling is a pure translation, the required
 * scrollOffset is `current firstVisibleItemScrollOffset + physical displacement`.
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

    animateScrollToItem(targetIndex, firstVisibleItemScrollOffset + delta)
}

/**
 * Scrolls a [LazyGridState] (episode grid) so the row containing [targetIndex] becomes visible.
 */
suspend fun LazyGridState.scrollToItemVisible(targetIndex: Int) {
    if (targetIndex < 0) return
    if (layoutInfo.visibleItemsInfo.any { it.index == targetIndex }) return
    animateScrollToItem(targetIndex)
}