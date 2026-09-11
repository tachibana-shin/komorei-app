package git.shin.komorei.ui.player

import androidx.compose.animation.*
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
import kotlin.math.roundToInt

/** Vertical-drag gesture regions on the video surface. */
private const val DRAG_NONE = 0
private const val DRAG_VOLUME = 1
private const val DRAG_BRIGHTNESS = 2

/** Bottom 20% of the video is reserved for control gestures (slider, buttons). */
private const val BOTTOM_CONTROL_ZONE_RATIO = 0.8f

/** Transient HUD shown while swiping volume (left half) or brightness (right half). */
private data class DragHud(val isVolume: Boolean, val level: Float)

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
    val showPosterBackground = (isLoading || streamData == null) && !isLocked

    var showControls by remember { mutableStateOf(true) }
    var interactionCounter by remember { mutableStateOf(0) }
    var pointerDown by remember { mutableStateOf(false) }
    var seekHudMs by remember { mutableStateOf(0L) }
    var fastForwarding by remember { mutableStateOf(false) }
    var dragHud by remember { mutableStateOf<DragHud?>(null) }

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset += offsetChange
    }

    // Auto-hide controls after 3.5s of inactivity while playing (paused while touching).
    LaunchedEffect(showControls, interactionCounter, isPlaying, pointerDown, isLocked, isLoading) {
        if (showControls && isPlaying && !pointerDown && !isLocked && !isLoading) {
            delay(3500)
            showControls = false
        }
    }

    // Auto-clear the double-tap seek HUD.
    LaunchedEffect(seekHudMs) {
        if (seekHudMs != 0L) {
            delay(900)
            seekHudMs = 0L
        }
    }

    Box(
        modifier = modifier
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
                        onSeekBy = { delta ->
                            if (!isLocked) {
                                seekRelative(player, delta)
                                seekHudMs = delta
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
                    .pointerInput(player, initialBrightness, isLocked) {
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
                                    DRAG_BRIGHTNESS -> initialBrightness
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
                                        ?: if (region == DRAG_VOLUME) player.volume else initialBrightness
                                    val level =
                                        (base + accumulated / (viewHeightPx / 2f)).coerceIn(0f, 1f)
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
                    onOpenSettings = onOpenSettings
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

        // Gesture HUDs
        AnimatedVisibility(
            visible = seekHudMs != 0L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            PlayerHudPill {
                Icon(
                    imageVector = if (seekHudMs < 0) Icons.Default.Replay10 else Icons.Default.Forward10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (seekHudMs < 0) {
                        stringResource(R.string.player_seek_backward, 10)
                    } else {
                        stringResource(R.string.player_seek_forward, 10)
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Volume / brightness swipe HUD
        AnimatedVisibility(
            visible = dragHud != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
        ) {
            dragHud?.let { hud ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (hud.isVolume) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.Filled.BrightnessMedium
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "${(hud.level * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
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

@Composable
private fun PlayerHudPill(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

private fun seekRelative(player: Player, deltaMs: Long) {
    val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
    player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
}
