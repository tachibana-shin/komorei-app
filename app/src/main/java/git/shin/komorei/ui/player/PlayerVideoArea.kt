package git.shin.komorei.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.material3.Player as ComposePlayer
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.ui.player.components.*
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Vertical-drag gesture regions on the video surface. */
private const val DRAG_NONE = 0
private const val DRAG_VOLUME = 1
private const val DRAG_BRIGHTNESS = 2

/** Bottom 20% of the video is reserved for control gestures (slider, buttons). */
private const val BOTTOM_CONTROL_ZONE_RATIO = 0.8f

/** Transient HUD shown while swiping volume (left half) or brightness (right half). */
private data class DragHud(val isVolume: Boolean, val level: Float)

/** YouTube-style double-tap seek HUD: remembered seek amount + where the tap landed. */
private data class SeekHud(val deltaMs: Long, val xFraction: Float)

@Composable
fun PlayerVideoArea(
    player: Player,
    title: String,
    episodeTitle: String,
    posterUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    isFullscreen: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    currentSpeed: Float,
    currentQuality: String?,
    isSubtitleEnabled: Boolean,
    introRange: LongRange?,
    outroRange: LongRange?,
    streamData: git.shin.komorei.model.StreamData?,
    playbackError: String?,
    initialBrightness: Float,
    isLocked: Boolean,
    videoResizeMode: Int,
    onMinimizeClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onRefresh: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStartFastForward: () -> Unit,
    onStopFastForward: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onToggleLock: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onOpenSubtitleMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onOpenServers: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Only show poster background if we don't have stream data yet (initial load)
        val showPosterBackground = streamData == null && !isLocked

        var showControls by remember { mutableStateOf(true) }
        var interactionCounter by remember { mutableStateOf(0) }
        var pointerDown by remember { mutableStateOf(false) }
        var seekHud by remember { mutableStateOf<SeekHud?>(null) }
        var fastForwarding by remember { mutableStateOf(false) }
        var dragHud by remember { mutableStateOf<DragHud?>(null) }

        // Always-current brightness for the volume/brightness drag base. MUST NOT be
        // used as a pointerInput key: brightness changes while dragging (via
        // onBrightnessChange) would otherwise cancel the drag coroutine mid-gesture
        // and skip onDragEnd, leaving the HUD pill stuck on screen.
        val currentInitialBrightness by rememberUpdatedState(initialBrightness)

        // Zoom state
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
            val newScale = (scale * zoomChange).coerceIn(1f, 4f)
            val maxX = (newScale - 1f) * widthPx / 2f
            val maxY = (newScale - 1f) * heightPx / 2f

            scale = newScale
            offset = if (newScale > 1f) {
                Offset(
                    x = (offset.x + offsetChange.x).coerceIn(-maxX, maxX),
                    y = (offset.y + offsetChange.y).coerceIn(-maxY, maxY)
                )
            } else {
                Offset.Zero
            }
        }

        // Auto-hide controls after 3.5s of inactivity while playing (paused while touching).
        LaunchedEffect(showControls, interactionCounter, isPlaying, pointerDown, isLocked, isLoading) {
            if (showControls && isPlaying && !pointerDown && !isLocked && !isLoading) {
                delay(3500)
                showControls = false
            }
        }

        // Auto-clear the double-tap seek HUD.
        LaunchedEffect(seekHud) {
            if (seekHud != null) {
                delay(900)
                seekHud = null
            }
        }

        // Watchdog: clear the volume/brightness pill even if onDragEnd /
        // onDragCancel is skipped (e.g. the drag coroutine is cancelled),
        // so it never lingers after the finger lifts.
        LaunchedEffect(dragHud) {
            if (dragHud != null) {
                delay(250)
                dragHud = null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clipToBounds()
                .transformable(state = transformableState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                ComposePlayer(
                    player = player,
                    showControls = false, // We use our own controls
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    modifier = Modifier
                        .fillMaxSize()
                        .playerGestures(
                            onToggleControls = {
                                showControls = !showControls
                                interactionCounter++
                            },
                            onSeekBy = { delta, xFraction ->
                                if (!isLocked) {
                                    seekRelative(player, delta)
                                    // Repeated double-taps accumulate in the HUD (like
                                    // YouTube: +10, +20, +30...) while each tap seeks.
                                    seekHud = SeekHud((seekHud?.deltaMs ?: 0L) + delta, xFraction)
                                    interactionCounter++
                                }
                            },
                            onFastForwardStart = {
                                if (!isLocked) {
                                    fastForwarding = true
                                    onStartFastForward()
                                    interactionCounter++
                                }
                            },
                            onFastForwardEnd = {
                                if (!isLocked) {
                                    fastForwarding = false
                                    onStopFastForward()
                                }
                            },
                            onPointerDownChange = { pointerDown = it },
                            onPointerMove = { interactionCounter++ }
                        )
                        .pointerInput(player, isLocked) {
                            if (isLocked) return@pointerInput
                            val viewWidthPx = size.width.toFloat()
                            val viewHeightPx = size.height.toFloat()
                            var region = DRAG_NONE
                            var accumulated = 0f

                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    region = when {
                                        offset.y > viewHeightPx * BOTTOM_CONTROL_ZONE_RATIO -> DRAG_NONE
                                        offset.x < viewWidthPx / 2f -> DRAG_BRIGHTNESS
                                        else -> DRAG_VOLUME
                                    }
                                    accumulated = 0f
                                    val base = when (region) {
                                        DRAG_VOLUME -> player.volume
                                        DRAG_BRIGHTNESS -> currentInitialBrightness
                                        else -> 0f
                                    }
                                    if (region != DRAG_NONE) {
                                        dragHud = DragHud(isVolume = region == DRAG_VOLUME, level = base)
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (region != DRAG_NONE) {
                                        accumulated -= dragAmount
                                        val base = dragHud?.level
                                            ?: if (region == DRAG_VOLUME) player.volume else currentInitialBrightness
                                        // Full-height swipe maps to the full 0..1 range:
                                        // a half-screen swipe = ±50%, a tiny swipe = tiny step.
                                        val level =
                                            (base + accumulated / viewHeightPx).coerceIn(0f, 1f)
                                        if (region == DRAG_VOLUME) {
                                            player.volume = level
                                        } else {
                                            onBrightnessChange(level)
                                        }
                                        dragHud = DragHud(isVolume = region == DRAG_VOLUME, level = level)
                                    }
                                },
                                onDragEnd = { dragHud = null },
                                onDragCancel = { dragHud = null }
                            )
                        }
                )
            }

            // Backdrop dim when controls shown
            AnimatedVisibility(
                visible = (showControls || showPosterBackground) && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                    if (showPosterBackground) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(0.6f)
                        )
                    }
                }
            }

            // Custom Overlays
            if (!isLocked) {
                // Header
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    PlayerControlHeader(
                        title = title,
                        episodeTitle = episodeTitle,
                        isSubtitleEnabled = isSubtitleEnabled,
                        isFullscreen = isFullscreen,
                        onBackClick = onMinimizeClick,
                        onSubtitleToggle = onToggleSubtitles,
                        onSubtitleLongClick = onOpenSubtitleMenu,
                        onSettingsClick = onOpenSettings
                    )
                }

                // Center
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CenterPlayerControl(
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        onPlayPause = {
                            if (isPlaying) player.pause() else player.play()
                            interactionCounter++
                        },
                        onSeekRelative = {
                            seekRelative(player, it)
                            // Center buttons accumulate in the HUD too.
                            seekHud = SeekHud((seekHud?.deltaMs ?: 0L) + it, if (it < 0) 0.25f else 0.75f)
                            interactionCounter++
                        }
                    )
                }

                // Footer
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    PlayerControlFooter(
                        positionMs = currentPositionMs,
                        durationMs = durationMs,
                        bufferedPositionMs = bufferedPositionMs,
                        introRange = introRange,
                        outroRange = outroRange,
                        isFullscreen = isFullscreen,
                        isPlaying = isPlaying,
                        currentSpeed = currentSpeed,
                        currentQuality = currentQuality,
                        onSeek = { player.seekTo(it); interactionCounter++ },
                        onToggleFullscreen = onToggleFullscreen,
                        onNextEpisode = onNextEpisode,
                        onOpenEpisodes = onOpenEpisodes,
                        onOpenServers = onOpenServers,
                        onOpenSettings = onOpenSettings,
                        onInteraction = { interactionCounter++ }
                    )
                }
            } else {
                // Unlock button only
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.player_unlock),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Gesture HUDs — YouTube-style double-tap seek indicator: a small icon
            // popping in at the tap position (no background circle/pill), then fading
            // away. The whole overlay only fades; the icon scales in place so it never
            // drifts from the tap position during the pop animation.
            AnimatedVisibility(
                visible = seekHud != null,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(220)),
                modifier = Modifier.fillMaxSize()
            ) {
                seekHud?.let { hud ->
                    // Keep the indicator fully on-screen near the edges.
                    val clampedFraction = hud.xFraction.coerceIn(0.15f, 0.85f)
                    // Pop animation runs once on entry (remember survives hud changes);
                    // repeated double-tap accumulations only update the text without replaying it.
                    val popScale = remember { Animatable(0.55f) }
                    LaunchedEffect(Unit) {
                        popScale.animateTo(1f, animationSpec = tween(220, easing = EaseOutBack))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = popScale.value
                                scaleY = popScale.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.offset {
                                IntOffset(
                                    x = ((clampedFraction * widthPx) - widthPx / 2f).roundToInt(),
                                    y = 0
                                )
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (hud.deltaMs < 0) {
                                        Icons.Default.Replay10
                                    } else {
                                        Icons.Default.Forward10
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (hud.deltaMs < 0) {
                                        stringResource(R.string.player_seek_backward, abs(hud.deltaMs / 1000))
                                    } else {
                                        stringResource(R.string.player_seek_forward, abs(hud.deltaMs / 1000))
                                    },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Volume / brightness swipe HUD — horizontal flex pill (icon | bar | value)
            // near the top of the screen, rounded + translucent like YouTube.
            AnimatedVisibility(
                visible = dragHud != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                dragHud?.let { hud ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hud.isVolume) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.Filled.BrightnessMedium
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { hud.level },
                            modifier = Modifier
                                .width(100.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50)),
                            color = AnimeRed,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Fixed-width value column so digits changing mid-drag
                        // (42% -> 43% -> ...) don't wiggle the whole pill.
                        Text(
                            text = "${(hud.level * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }
            }

            // Global loading indicator (always visible when buffering)
            if ((isLoading || streamData == null) && !isLocked) {
                CircularProgressIndicator(
                    color = AnimeRed,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }
        }
    }
}

private fun seekRelative(player: Player, deltaMs: Long) {
    val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
    player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
}
