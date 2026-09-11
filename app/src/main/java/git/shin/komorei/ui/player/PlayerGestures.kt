package git.shin.komorei.ui.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Double-tap / button seek increment (ms), matching the app's rewind/forward buttons. */
private const val SEEK_INCREMENT_MS = 10_000L

/**
 * YouTube-style player gestures for the video surface.
 *
 * Ported from the official AndroidX media3 demo
 * (`demos/compose/.../layout/modifiers.kt` — Apache-2.0), simplified to plain
 * callbacks bound to the app's `PlayerViewModel`:
 *  - single tap        -> [onToggleControls]
 *  - double tap        -> [onSeekBy] ±10s (left half seeks back, right half forward),
 *    with the tap horizontal fraction so the caller can draw the new YouTube-style
 *    circular seek indicator at the tap position
 *  - long press (hold) -> [onFastForwardStart] while held, [onFastForwardEnd] on release
 *  - pointer down/move -> [onPointerDownChange] / [onPointerMove] so the caller can
 *    pause the auto-hide timer while the user is interacting with the video
 */
@Composable
fun Modifier.playerGestures(
    onToggleControls: () -> Unit,
    onSeekBy: (deltaMs: Long, xFraction: Float) -> Unit,
    onFastForwardStart: () -> Unit,
    onFastForwardEnd: () -> Unit,
    onPointerDownChange: ((Boolean) -> Unit)? = null,
    onPointerMove: (() -> Unit)? = null
): Modifier {
    return this
        .pointerInput(Unit) {
            coroutineScope {
                // Track pointer state so the caller can keep controls visible while touching.
                launch {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }
                            onPointerDownChange?.invoke(anyPressed)
                            if (event.type == PointerEventType.Move) {
                                onPointerMove?.invoke()
                            }
                        }
                    }
                }

                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { offset ->
                        val delta = if (offset.x < size.width / 2f) {
                            -SEEK_INCREMENT_MS
                        } else {
                            SEEK_INCREMENT_MS
                        }
                        val xFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekBy(delta, xFraction)
                    },
                    onLongPress = { offset ->
                        // Matches the official demo: hold the right half to fast-forward.
                        if (offset.x >= size.width / 2f) {
                            onFastForwardStart()
                        }
                    },
                    onPress = {
                        try {
                            // Wait until the pointer is released or the gesture is cancelled.
                            tryAwaitRelease()
                        } catch (_: CancellationException) {
                            // Cancelled (e.g. turned into a drag) — stop any active fast-forward.
                        } finally {
                            onFastForwardEnd()
                        }
                    }
                )
            }
        }
}