package git.shin.komorei.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.material3.Player as ComposePlayer
import git.shin.komorei.R
import git.shin.komorei.ui.theme.AnimeGreen
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.Locale

/** Playback speed presets shown in the player settings menu. */
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Vertical-drag gesture regions on the video surface. */
private const val DRAG_NONE = 0
private const val DRAG_VOLUME = 1
private const val DRAG_BRIGHTNESS = 2

/** Bottom 20% of the video is reserved for control gestures (slider, buttons). */
private const val BOTTOM_CONTROL_ZONE_RATIO = 0.8f

/** Transient HUD shown while swiping volume (left half) or brightness (right half). */
private data class DragHud(val isVolume: Boolean, val level: Float)

/**
 * Media3 Compose "Player" surface + YouTube-style controls, all driven by the
 * [Player] engine owned by [PlayerViewModel]:
 *  - single tap toggles controls, double-tap seeks ±10s, hold (right half) fast-forwards
 *  - vertical swipe: left half = brightness, right half = volume (real engine values)
 *  - top bar: minimize, title, refresh (re-resolve stream), settings (playback speed)
 *  - center: rewind 10s | play/pause | forward 10s
 *  - bottom: timeline scrubber + duration + AI summary + fullscreen;
 *    when controls are hidden a thin progress line stays visible at the bottom edge
 */
@Composable
fun PlayerVideoArea(
    player: Player,
    title: String,
    episodeTitle: String,
    isPlaying: Boolean,
    isFullscreen: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    currentSpeed: Float,
    playbackError: String?,
    initialBrightness: Float,
    onMinimizeClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onRefresh: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStartFastForward: () -> Unit,
    onStopFastForward: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onOpenEpisodes: (() -> Unit)? = null,
    onOpenServers: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onOpenSummaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showControls by remember { mutableStateOf(true) }
    var interactionCounter by remember { mutableStateOf(0) }
    var pointerDown by remember { mutableStateOf(false) }
    var seekHudMs by remember { mutableStateOf(0L) }
    var fastForwarding by remember { mutableStateOf(false) }
    var dragHud by remember { mutableStateOf<DragHud?>(null) }

    val progressFraction = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    // Auto-hide controls after 3.5s of inactivity while playing (paused while touching).
    LaunchedEffect(showControls, interactionCounter, isPlaying, pointerDown) {
        if (showControls && isPlaying && !pointerDown) {
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

    Box(modifier = modifier.fillMaxSize()) {
        ComposePlayer(
            player = player,
            showControls = showControls,
            // TextureView (not the default SurfaceView): the video sits inside a draggable
            // sheet that is offset/translated and re-laid-out on fullscreen. A SurfaceView is
            // composited in its own window layer that ignores Compose offset/clip/scale and
            // punches a black rectangle over the rest of the app; TextureView renders through
            // the normal view hierarchy so it tracks the sheet and clips correctly.
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier
                .fillMaxSize()
                .playerGestures(
                    onToggleControls = {
                        showControls = !showControls
                        interactionCounter++
                    },
                    onSeekBy = { delta ->
                        seekRelative(player, delta)
                        seekHudMs = delta
                        interactionCounter++
                    },
                    onFastForwardStart = {
                        fastForwarding = true
                        onStartFastForward()
                        interactionCounter++
                    },
                    onFastForwardEnd = {
                        fastForwarding = false
                        onStopFastForward()
                    },
                    onPointerDownChange = { pointerDown = it },
                    onPointerMove = { interactionCounter++ }
                )
                .pointerInput(player, initialBrightness) {
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
                                // Swiping up increases the level (dragAmount is positive when
                                // the finger moves downwards).
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
                },
            topControls = { _, visible ->
                AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
                    playerTopControls(
                        title = title,
                        episodeTitle = episodeTitle,
                        currentSpeed = currentSpeed,
                        onMinimizeClick = onMinimizeClick,
                        onRefreshClick = {
                            onRefresh()
                            interactionCounter++
                        },
                        onSpeedChange = {
                            onSpeedChange(it)
                            interactionCounter++
                        },
                        onOpenEpisodes = onOpenEpisodes?.let {
                            {
                                it()
                                interactionCounter++
                            }
                        },
                        onOpenServers = onOpenServers?.let {
                            {
                                it()
                                interactionCounter++
                            }
                        }
                    )
                }
            },
            centerControls = { _, visible ->
                AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
                    playerCenterControls(
                        player = player,
                        isPlaying = isPlaying,
                        onInteract = {
                            interactionCounter++
                            showControls = true
                        }
                    )
                }
            },
            bottomControls = { _, visible ->
                AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
                    playerBottomControls(
                        player = player,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = {
                            onToggleFullscreen()
                            interactionCounter++
                        },
                        onNextEpisode = onNextEpisode,
                        onOpenSummaryClick = onOpenSummaryClick,
                        onInteract = { interactionCounter++ }
                    )
                }
            },
            errorOverlay = null
        )

        // Double-tap seek indicator ("+10 giây" / "-10 giây").
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

        // "Hold to fast-forward" indicator ("2x").
        AnimatedVisibility(
            visible = fastForwarding,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
        ) {
            PlayerHudPill {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = speedLabel(2f),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Volume / brightness swipe HUD (vertical bar on the side).
        AnimatedVisibility(
            visible = dragHud != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)
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
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "${(hud.level * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Thin progress line at the bottom edge, YouTube-style, kept when controls are hidden.
        if (!showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0x40FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(AnimeRed)
                )
            }
        }

        // Playback engine failure (dead URL, network error, ...) surfaced after the
        // stream resolved — show an error + retry instead of a silent black screen.
        if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = playbackError,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = onRefresh) {
                        Text(
                            text = stringResource(R.string.action_retry),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Rounded dark pill used by the gesture HUDs (seek / fast-forward / drag level). */
@Composable
private fun PlayerHudPill(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xA6000000))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun BoxScope.playerTopControls(
    title: String,
    episodeTitle: String,
    currentSpeed: Float,
    onMinimizeClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onOpenEpisodes: (() -> Unit)? = null,
    onOpenServers: (() -> Unit)? = null
) {
    var speedMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMinimizeClick,
            modifier = Modifier.testTag("player_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_minimize_player),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = episodeTitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        IconButton(
            onClick = onRefreshClick,
            modifier = Modifier
                .size(36.dp)
                .testTag("player_refresh_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.cd_refresh),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Settings -> playback speed menu
        Box {
            IconButton(
                onClick = { speedMenuOpen = true },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("player_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = speedMenuOpen,
                onDismissRequest = { speedMenuOpen = false }
            ) {
                Text(
                    text = stringResource(R.string.player_speed_title),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                PLAYBACK_SPEEDS.forEach { speed ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = speedLabel(speed),
                                color = if (speed == currentSpeed) AnimeGreen else TextPrimary
                            )
                        },
                        leadingIcon = {
                            if (speed == currentSpeed) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AnimeGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            onSpeedChange(speed)
                            speedMenuOpen = false
                        }
                    )
                }
            }
        }

            // In-player episode/season list -> opens the episode/season side menu.
            if (onOpenEpisodes != null) {
                IconButton(
                    onClick = onOpenEpisodes,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("player_episodes_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = stringResource(R.string.cd_episodes_list),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // In-player server picker -> opens the server side menu.
            if (onOpenServers != null) {
                IconButton(
                    onClick = onOpenServers,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("player_servers_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = stringResource(R.string.cd_server_list),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
    }
}

@Composable
private fun BoxScope.playerCenterControls(
    player: Player,
    isPlaying: Boolean,
    onInteract: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .align(Alignment.Center),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                seekRelative(player, -10_000L)
                onInteract()
            },
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0x33000000))
                .testTag("player_rewind_10")
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = stringResource(R.string.cd_rewind_10),
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (player.isPlaying) player.pause() else player.play()
                    onInteract()
                }
                .testTag("player_play_pause_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(R.string.cd_pause)
                } else {
                    stringResource(R.string.cd_play)
                },
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        IconButton(
            onClick = {
                seekRelative(player, 10_000L)
                onInteract()
            },
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0x33000000))
                .testTag("player_forward_10")
        ) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = stringResource(R.string.cd_forward_10),
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun BoxScope.playerBottomControls(
    player: Player,
    currentPositionMs: Long,
    durationMs: Long,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenSummaryClick: (() -> Unit)?,
    onNextEpisode: (() -> Unit)? = null,
    onInteract: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(currentPositionMs)} / ${formatTime(if (durationMs > 0) durationMs else 1440000L)}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenSummaryClick != null) {
                    IconButton(
                        onClick = onOpenSummaryClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_ai_summary),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (onNextEpisode != null) {
                    IconButton(
                        onClick = {
                            onNextEpisode()
                            onInteract()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("player_next_episode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.cd_next_episode),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = {
                        onToggleFullscreen()
                        onInteract()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("player_fullscreen_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = stringResource(R.string.cd_fullscreen),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        val safeDuration = if (durationMs > 0) durationMs.toFloat() else 1440000f
        val progress = (currentPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)

        Slider(
            value = progress,
            onValueChange = { frac ->
                player.seekTo((frac * safeDuration).toLong())
                onInteract()
            },
            colors = SliderDefaults.colors(
                thumbColor = AnimeGreen,
                activeTrackColor = AnimeGreen,
                inactiveTrackColor = Color(0x44FFFFFF)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .testTag("player_timeline_slider")
        )
    }
}

private fun seekRelative(player: Player, deltaMs: Long) {
    val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
    player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
}

/** "1" -> "1", "1.25" -> "1.25" — pure value formatting (no UI text). */
private fun speedValue(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()

@Composable
private fun speedLabel(speed: Float): String =
    stringResource(R.string.player_speed_format, speedValue(speed))

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}