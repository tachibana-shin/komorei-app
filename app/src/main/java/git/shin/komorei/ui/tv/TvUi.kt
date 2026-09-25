package git.shin.komorei.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import git.shin.komorei.ui.theme.AnimeRed
import kotlinx.coroutines.delay

/*
 * Android TV / leanback support.
 *
 * The app is touch-first, but every interactive surface can also be driven by a
 * TV remote when the device reports the television UI mode: Compose already
 * handles D-pad focus traversal (focusable `clickable`s inside
 * `LazyRow`/`LazyColumn`/`LazyVerticalGrid` are scrolled into view as focus
 * moves), so this file only supplies two pieces:
 *
 *  - [LocalTvMode] + [rememberIsTvMode] — whether the app currently runs on a
 *    TV. Provided once at the activity root ([git.shin.komorei.MainActivity]).
 *    Phone/tablet builds (and ALL Robolectric tests, which run under the phone
 *    uiMode) keep `false`, so every highlight below is a no-op off TV — zero
 *    effect on existing touch UX and screenshots.
 *  - [Modifier.tvFocus] — a focus highlight (scale-up + accent ring, animated
 *    on focus gain/loss) that makes the currently focused item obvious on a TV
 *    where there is no hover cursor. It is a PURE visual overlay: it uses
 *    `graphicsLayer` (no layout change, so Lazy rows/grids don't re-flow) plus
 *    a `border` stroke, and never intercepts clicks or key events.
 *
 * Note the TextureView rule from AGENTS.md does not apply here — the modifier
 * is only ever placed on cards/chips/buttons, never on the player surface or
 * any of its ancestor boxes.
 */

/** `true` when the app runs on a television device (D-pad/remote input). */
val LocalTvMode = staticCompositionLocalOf { false }

/** Detects the TV uiMode both via [UiModeManager] and the resource config. */
@Composable
fun rememberIsTvMode(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
    }
}

/**
 * TV focus highlight. On a TV (per [LocalTvMode]) the item scales up slightly
 * and an animated accent ring draws around [shape] while focused; off-TV this
 * is the identity modifier and changes nothing.
 *
 * Apply it to the same chain that carries the item's `clickable`/focus —
 * e.g. `Modifier.tvFocus(shape = RoundedCornerShape(12.dp)).clickable(...)` —
 * so [androidx.compose.ui.focus.onFocusChanged] observes the element's focus.
 */
@Composable
fun Modifier.tvFocus(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    scale: Float = 1.06f,
    borderWidth: Dp = 3.dp,
    borderColor: Color = AnimeRed,
): Modifier {
    if (!LocalTvMode.current || !enabled) return this
    var isFocused by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "tvFocus",
    )
    return this
        .onFocusChanged { isFocused = it.isFocused }
        .graphicsLayer {
            val s = 1f + (scale - 1f) * progress
            scaleX = s
            scaleY = s
        }.border(width = borderWidth, color = borderColor.copy(alpha = progress), shape = shape)
}

/**
 * Requests focus for [focusRequester] shortly after it enters composition —
 * the TV equivalent of "the screen opened, land on the primary control" (Home
 * tab bar, search field, …). A no-op everywhere except TV mode. [delayMillis]
 * lets the first frame render so the requester has attached; the target may
 * still be loading, in which case the first D-pad press starts navigation.
 */
@Composable
fun TvInitialFocus(
    focusRequester: FocusRequester,
    enabled: Boolean = LocalTvMode.current,
    delayMillis: Long = 200,
) {
    if (!enabled) return
    LaunchedEffect(focusRequester) {
        delay(delayMillis)
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // Target not attached yet (data still loading) — navigation just
            // starts from the first D-pad press instead.
        }
    }
}
