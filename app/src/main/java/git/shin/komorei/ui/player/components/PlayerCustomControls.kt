package git.shin.komorei.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerControlHeader(
    title: String,
    episodeTitle: String,
    isSubtitleEnabled: Boolean,
    isFullscreen: Boolean,
    onBackClick: () -> Unit,
    onSubtitleToggle: () -> Unit,
    onSubtitleLongClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconSize = if (isFullscreen) 28.dp else 24.dp
    val titleSize = if (isFullscreen) 18.sp else 15.sp
    val subtitleSize = if (isFullscreen) 14.sp else 12.sp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_minimize_player),
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (titleSize.value * 1.2f).sp
                )
                Text(
                    text = episodeTitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = subtitleSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (subtitleSize.value * 1.2f).sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(iconSize + 16.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = onSubtitleToggle,
                        onLongClick = onSubtitleLongClick,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = (iconSize / 2) + 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSubtitleEnabled) Icons.Default.ClosedCaption else Icons.Outlined.ClosedCaption,
                    contentDescription = stringResource(R.string.player_subtitle),
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
            Box(
                modifier = Modifier
                    .size(iconSize + 16.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClick = onSettingsClick,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = (iconSize / 2) + 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedProgressSlider(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    introRange: LongRange?,
    outroRange: LongRange?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val bufferedProgress = if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs else 0f

    Slider(
        value = progress.coerceIn(0f, 1f),
        onValueChange = { onSeek((it * durationMs).toLong()) },
        modifier = modifier.height(25.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .offset(y = 2.dp)
                    .background(Color.White, CircleShape)
            )
        },
        track = { sliderState ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)) {
                    val width = size.width
                    val height = size.height

                    // Background
                    drawRect(
                        color = Color.White.copy(alpha = 0.24f),
                        size = size
                    )

                    // Buffered
                    drawRect(
                        color = Color.White.copy(alpha = 0.4f),
                        size = Size(width * bufferedProgress.coerceIn(0f, 1f), height)
                    )

                    // Intro
                    introRange?.let {
                        val start = (it.first.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(
                            0f,
                            1f
                        ) * width
                        val end = (it.last.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(
                            0f,
                            1f
                        ) * width
                        drawRect(
                            color = Color.Green.copy(alpha = 0.6f),
                            topLeft = Offset(start, 0f),
                            size = Size(end - start, height)
                        )
                    }

                    // Outro
                    outroRange?.let {
                        val start = (it.first.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(
                            0f,
                            1f
                        ) * width
                        val end = (it.last.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(
                            0f,
                            1f
                        ) * width
                        drawRect(
                            color = Color.Green.copy(alpha = 0.6f),
                            topLeft = Offset(start, 0f),
                            size = Size(end - start, height)
                        )
                    }

                    // Active
                    drawRect(
                        color = AnimeRed,
                        size = Size(width * progress.coerceIn(0f, 1f), height)
                    )
                }
            }
        }
    )
}

@Composable
fun PlayerControlFooter(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    introRange: LongRange?,
    outroRange: LongRange?,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    currentSpeed: Float,
    currentQuality: String?,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onNextEpisode: (() -> Unit)?,
    onOpenEpisodes: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textStyle = TextStyle(
        color = Color.White,
        fontSize = if (isFullscreen) 14.sp else 13.sp,
        fontWeight = FontWeight.Medium
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp, 0.dp, 12.dp, 0.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                style = textStyle
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 24.dp),
                        onClick = onToggleFullscreen
                    ).offset(4.dp, 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = stringResource(R.string.cd_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(if (isFullscreen) 28.dp else 24.dp)
                )
            }
        }

        SegmentedProgressSlider(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            introRange = introRange,
            outroRange = outroRange,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )

        if (isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onNextEpisode != null) {
                        Button(
                            onClick = onNextEpisode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(
                                    alpha = 0.1f
                                )
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Tiếp", color = Color.White, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    IconButton(onClick = onOpenEpisodes) {
                        Icon(
                            Icons.Default.PlaylistPlay,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpenServers) {
                        Icon(
                            Icons.Default.Dns,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Server", color = Color.White, fontSize = 13.sp)
                    }
                    TextButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.HighQuality,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(currentQuality ?: "Auto", color = Color.White, fontSize = 13.sp)
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text(
                            "${
                                if (currentSpeed == currentSpeed.toInt()
                                        .toFloat()
                                ) currentSpeed.toInt().toString() else currentSpeed.toString()
                            }x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CenterPlayerControl(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp),
                    onClick = { onSeekRelative(-10000L) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(32.dp))

        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isLoading) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 28.dp),
                            onClick = { onPlayPause() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(32.dp))

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp),
                    onClick = { onSeekRelative(10000L) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun PlayerSideSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp),
                color = BackgroundDark.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp, 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary)
                        }
                    }
                    HorizontalDivider(
                        color = CardBorderDark,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        content()
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
}
