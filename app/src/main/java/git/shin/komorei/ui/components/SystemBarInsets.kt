package git.shin.komorei.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * The system navigation-bar height (in [Dp]), measured from the HOST window.
 *
 * [androidx.compose.material3.ModalBottomSheet] composes its content inside a
 * separate Dialog window, and that window does NOT reliably receive system-bar
 * insets — on API 30 emulators it reports zero insets (the Window Manager just
 * clamps the sheet to the decor area). As a result, the Material3 default
 * `contentWindowInsets` (`WindowInsets.safeDrawing` via `windowInsetsPadding`)
 * is frequently a NO-OP inside the sheet, and on edge-to-edge devices tall sheet
 * content gets drawn underneath the navigation bar.
 *
 * Reading the inset HERE — at the sheet's call site, i.e. inside the app's own
 * window where insets ARE delivered — returns the real value, which the sheet's
 * content turns into explicit bottom padding (`navBarBottom + N.dp`).
 *
 * Call this OUTSIDE the `ModalBottomSheet { ... }` content lambda; the content
 * lambda runs inside the Dialog window and would read zero.
 */
@Composable
fun rememberSystemNavigationBarBottom(): Dp {
    val density = LocalDensity.current
    val bottom = WindowInsets.navigationBars.getBottom(density)
    return with(density) { bottom.toDp() }
}
