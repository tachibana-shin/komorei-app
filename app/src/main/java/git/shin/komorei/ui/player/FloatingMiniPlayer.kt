package git.shin.komorei.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.material3.Player as ComposePlayer
import coil.compose.AsyncImage
import git.shin.komorei.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Collapsed floating mini player (YouTube-style PiP, rendered in-app — not system PiP).
 *
 * A 16:9 bubble, free-dragged like a chat bubble; releasing it springs to the NEAREST
 * of the four screen corners (see [MiniPlayerGeometry]). Minimal controls overlaid on the
 * video: play/pause (top-left) + close (top-right) — no progress bar. Tapping the video
 * expands back to the full sheet.
 *
 * The whole bubble is clipped to a rounded rect, so the TextureView inside keeps its
 * corners (the player surface MUST stay a TextureView — see AGENTS.md).
 *
 * The bubble's top-left is HOISTED: [initialPosition] on mount + [onPositionChange] while
 * dragging/snapping, so `VideoPlayerSheet` keeps the corner across collapse/expand cycles.
 * It simply MOUNTS at [initialPosition] — the "mở mini player bằng animate" pop-in is
 * done by the sheet's AnimatedVisibility enter (scale + fade); this composable does not
 * animate its own appearance. Drag bounds are clamped above `bottomInsetPx` so the
 * bubble can't cover the app's bottom toolbar.
 */
@Composable
fun FloatingMiniPlayer(
    player: Player?,
    posterUrl: String,
    isPlaying: Boolean,
    initialPosition: Offset,
    onPositionChange: (Offset) -> Unit,
    onExpand: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInsetPx: Float = 0f
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val cornerRadius = RoundedCornerShape(14.dp)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val geo = computeMiniPlayerGeometry(maxW, maxH, density, bottomInsetPx)
        val width = geo.width
        val height = geo.height

        // Bubble top-left (px). A single Animatable<Offset> tracks 1:1 with the finger
        // and springs to the nearest corner on release. Initialized at the remembered
        // corner, clamped above the bottom toolbar.
        val resting = Offset(
            initialPosition.x.coerceIn(0f, maxW - width),
            initialPosition.y.coerceIn(0f, maxH - height - bottomInsetPx)
        )
        val position = remember {
            Animatable(resting, Offset.VectorConverter)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                .size(
                    with(density) { width.toDp() },
                    with(density) { height.toDp() }
                )
                .shadow(12.dp, cornerRadius)
                .clip(cornerRadius)
                .background(Color.Black)
                .pointerInput(Unit, maxW, maxH, width, height, bottomInsetPx) {
                    detectDragGestures(
                        onDragStart = {
                            scope.launch { position.stop() }
                        },
                        onDragEnd = {
                            // Snap to the nearest screen corner, springing the bubble home.
                            val target = geo.nearestCorner(position.value)
                            val spec = spring<Offset>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
                            scope.launch {
                                position.animateTo(target, spec)
                                onPositionChange(position.value)
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            position.snapTo(
                                Offset(
                                    (position.value.x + dragAmount.x).coerceIn(0f, maxW - width),
                                    (position.value.y + dragAmount.y).coerceIn(0f, maxH - height - bottomInsetPx)
                                )
                            )
                            onPositionChange(position.value)
                        }
                    }
                }
                .testTag("mini_player")
        ) {
            // Poster behind the video surface — visible until the first frame draws.
            // Clipped to the same rounded corners as the bubble.
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(cornerRadius)
            )

            if (player != null) {
                ComposePlayer(
                    player = player,
                    showControls = false, // the floating bubble has its own minimal controls
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Tap anywhere outside the buttons to expand back to the full player
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onExpand)
            )

            // Minimal controls overlaid on the corners: play/pause top-left, close
            // top-right — small translucent circles, tucked in with 8dp margins.
            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .testTag("mini_player_play_pause")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.cd_pause else R.string.cd_play
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .testTag("mini_player_close")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_close_player),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}