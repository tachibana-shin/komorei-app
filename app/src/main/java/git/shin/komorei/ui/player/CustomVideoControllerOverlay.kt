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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.ui.theme.AnimeGreen
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomVideoControllerOverlay(
    title: String,
    episodeTitle: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onMinimizeClick: () -> Unit,
    onOpenSummaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isControlsVisible by remember { mutableStateOf(true) }
    var interactionCounter by remember { mutableStateOf(0) }

    // Volume & Brightness HUD
    var brightnessLevel by remember { mutableFloatStateOf(0.85f) }
    var volumeLevel by remember { mutableFloatStateOf(0.80f) }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var showVolumeHud by remember { mutableStateOf(false) }

    // Auto-hide controls after 3.5 seconds of inactivity
    LaunchedEffect(isControlsVisible, interactionCounter, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(3500)
            isControlsVisible = false
        }
    }

    // Hide HUDs after dragging
    LaunchedEffect(showBrightnessHud) {
        if (showBrightnessHud) {
            delay(1500)
            showBrightnessHud = false
        }
    }
    LaunchedEffect(showVolumeHud) {
        if (showVolumeHud) {
            delay(1500)
            showVolumeHud = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isControlsVisible = !isControlsVisible
                interactionCounter++
            }
    ) {
        // Left half for brightness swipe gestures
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val delta = -dragAmount / 350f
                        brightnessLevel = (brightnessLevel + delta).coerceIn(0.05f, 1f)
                        showBrightnessHud = true
                        interactionCounter++
                    }
                }
        )

        // Right half for volume swipe gestures
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val delta = -dragAmount / 350f
                        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                        showVolumeHud = true
                        interactionCounter++
                    }
                }
        )

        // Brightness HUD Indicator
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC111520))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessMedium,
                    contentDescription = stringResource(R.string.cd_brightness),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(brightnessLevel * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Volume HUD Indicator
        AnimatedVisibility(
            visible = showVolumeHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC111520))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.cd_volume),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(volumeLevel * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Animated Controls Overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xDD000000),
                                Color(0x66000000),
                                Color(0xDD000000)
                            )
                        )
                    )
            ) {
                // Top Bar: Back/Minimize + Title & Episode + Refresh + Settings Gear
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
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
                        onClick = { interactionCounter++ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { interactionCounter++ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Controls: Rewind 10s | Play/Pause | Forward 10s
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.75f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            onRewind10()
                            interactionCounter++
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

                    // Main Play / Pause Circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable {
                                onPlayPauseToggle()
                                interactionCounter++
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

                    // Fast Forward 10s
                    IconButton(
                        onClick = {
                            onForward10()
                            interactionCounter++
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

                // Bottom Timeline Scrubber + Duration + Sparkle & Fullscreen
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
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
                                        contentDescription = "AI Summary",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            IconButton(
                                onClick = { interactionCounter++ },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = stringResource(R.string.cd_fullscreen),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Video progress scrubber bar
                    val safeDuration = if (durationMs > 0) durationMs.toFloat() else 1440000f
                    val progress = (currentPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)

                    Slider(
                        value = progress,
                        onValueChange = { frac ->
                            val targetMs = (frac * safeDuration).toLong()
                            onSeekTo(targetMs)
                            interactionCounter++
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
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
