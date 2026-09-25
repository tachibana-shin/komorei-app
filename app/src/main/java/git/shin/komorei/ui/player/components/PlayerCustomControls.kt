package git.shin.komorei.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.ui.player.SkipKind
import git.shin.komorei.ui.theme.AnimeBlue
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus
import git.shin.komorei.ui.utils.formatDuration

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
    modifier: Modifier = Modifier,
) {
    val iconSize = if (isFullscreen) 28.dp else 24.dp
    val titleSize = if (isFullscreen) 18.sp else 15.sp
    val subtitleSize = if (isFullscreen) 14.sp else 12.sp

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBackClick,
                // TV focus highlight (no-op on phones) — sibling overlay of the
                // player surface, so the graphicsLayer is safe.
                modifier = Modifier.tvFocus(shape = CircleShape, scale = 1.12f, borderColor = Color.White),
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_minimize_player),
                    tint = Color.White,
                    modifier = Modifier.size(iconSize),
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
                    lineHeight = (titleSize.value * 1.2f).sp,
                )
                Text(
                    text = episodeTitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = subtitleSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (subtitleSize.value * 1.2f).sp,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(iconSize + 16.dp)
                        // TV focus highlight (no-op on phones); white ring + scale
                        // so the icon reads on the video surface.
                        .tvFocus(shape = CircleShape, scale = 1.12f, borderColor = Color.White)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = onSubtitleToggle,
                            onLongClick = onSubtitleLongClick,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = (iconSize / 2) + 8.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSubtitleEnabled) Icons.Default.ClosedCaption else Icons.Outlined.ClosedCaption,
                    contentDescription = stringResource(R.string.player_subtitle),
                    tint = Color.White,
                    modifier = Modifier.size(iconSize),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(iconSize + 16.dp)
                        .tvFocus(shape = CircleShape, scale = 1.12f, borderColor = Color.White)
                        .clip(CircleShape)
                        .clickable(
                            onClick = onSettingsClick,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = (iconSize / 2) + 8.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = Color.White,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
fun SegmentedProgressSlider(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    introRange: LongRange?,
    outroRange: LongRange?,
    onSeek: (Long) -> Unit,
    onSeekPreview: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(0L)
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val bufferedProgress = if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs else 0f

    // Custom relative-drag scrubber (youtube-style):
    //  - Dragging NEVER teleports the thumb to the finger; instead it records the
    //    swipe DELTA from the grab point and moves the progress relative to the
    //    position when the drag started.
    //  - While dragging the thumb follows our local state (not the 250ms poller,
    //    so it cannot be yanked back and stutter).
    //  - The engine is only seeked once on release; a quick tap without movement
    //    seeks to the tapped time (absolute).
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progress) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartFraction by remember { mutableFloatStateOf(0f) }

    // Latest values that survive recomposition for use inside the gesture coroutine.
    val latestProgress by rememberUpdatedState(progress)
    val latestDuration by rememberUpdatedState(duration)

    val renderedFraction = if (isDragging) dragFraction else progress.coerceIn(0f, 1f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(24.dp)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(renderedFraction, 0f..1f)
                }.pointerInput(Unit) {
                    try {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Consume the down so the parent sheet drag / gestures don't also react.
                            down.consume()
                            val pointerId = down.id
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val startFraction = latestProgress.coerceIn(0f, 1f)

                            isDragging = true
                            dragStartFraction = startFraction
                            dragStartX = down.position.x
                            dragFraction = startFraction
                            onSeekPreview((startFraction * latestDuration).toLong())

                            var dragMoved = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change =
                                    event.changes.firstOrNull { it.id == pointerId } ?: continue
                                change.consume()

                                if (!change.pressed) {
                                    // Gesture finished.
                                    if (dragMoved) {
                                        onSeek((dragFraction * latestDuration).toLong())
                                    } else {
                                        // Tap without movement → absolute seek to the tapped time.
                                        val f = (down.position.x / widthPx).coerceIn(0f, 1f)
                                        onSeek((f * latestDuration).toLong())
                                    }
                                    break
                                }

                                if (!dragMoved &&
                                    (change.position - down.position).getDistance() >= viewConfiguration.touchSlop
                                ) {
                                    dragMoved = true
                                }

                                if (dragMoved) {
                                    // Relative scrub: base progress + (finger delta / track width).
                                    val f =
                                        (dragStartFraction + (change.position.x - dragStartX) / widthPx)
                                            .coerceIn(0f, 1f)
                                    dragFraction = f
                                    onSeekPreview((f * latestDuration).toLong())
                                }
                            }
                        }
                    } finally {
                        isDragging = false
                    }
                },
    ) {
        val trackH = 4.dp.toPx()
        val trackY = size.height / 2f
        val thumbR = 6.dp.toPx()

        // Background
        drawRect(
            color = Color.White.copy(alpha = 0.24f),
            topLeft = Offset(0f, trackY - trackH / 2f),
            size = Size(size.width, trackH),
        )

        // Buffered
        drawRect(
            color = Color.White.copy(alpha = 0.4f),
            topLeft = Offset(0f, trackY - trackH / 2f),
            size = Size(size.width * bufferedProgress.coerceIn(0f, 1f), trackH),
        )

        // Active (đã phát)
        drawRect(
            color = AnimeRed,
            topLeft = Offset(0f, trackY - trackH / 2f),
            size = Size(size.width * renderedFraction, trackH),
        )

        // Intro/Outro vẽ TRÊN vạch đỏ đã phát — highlight intro/outro có độ ưu tiên
        // cao nhất trong các segment (chỉ thumb nằm trên): khi playhead đã đi qua
        // vùng intro/outro, vạch xanh dương vẫn hiển thị đầy đủ, không bị đỏ che.

        // Intro (xanh dương)
        introRange?.let {
            val start =
                (it.first.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f) * size.width
            val end = (it.last.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f) * size.width
            drawRect(
                color = AnimeBlue.copy(alpha = 0.6f),
                topLeft = Offset(start, trackY - trackH / 2f),
                size = Size(end - start, trackH),
            )
        }

        // Outro (xanh dương)
        outroRange?.let {
            val start =
                (it.first.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f) * size.width
            val end = (it.last.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f) * size.width
            drawRect(
                color = AnimeBlue.copy(alpha = 0.6f),
                topLeft = Offset(start, trackY - trackH / 2f),
                size = Size(end - start, trackH),
            )
        }

        // Thumb
        drawCircle(
            color = Color.White,
            radius = thumbR,
            center = Offset(size.width * renderedFraction.coerceIn(0f, 1f), trackY),
        )
    }
}

/**
 * SponsorBlock-style skip pill shown in the player overlay while the playhead is
 * inside the intro/outro range. Tapping it skips past the segment ([kind] →
 * localized label, "mở đầu"/"kết thúc").
 */
@Composable
fun SkipSegmentPill(
    kind: SkipKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                // TV focus highlight (no-op on phones).
                .tvFocus(shape = RoundedCornerShape(50), scale = 1.05f)
                .clip(RoundedCornerShape(50))
                .background(SurfaceDark.copy(alpha = 0.92f))
                .border(BorderStroke(1.dp, CardBorderDark), RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text =
                stringResource(
                    when (kind) {
                        SkipKind.INTRO -> R.string.player_skip_intro
                        SkipKind.OUTRO -> R.string.player_skip_outro
                    },
                ),
            color = TextPrimary,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
    onInteraction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val textStyle =
        TextStyle(
            color = Color.White,
            fontSize = if (isFullscreen) 14.sp else 13.sp,
            fontWeight = FontWeight.Medium,
        )

    // Live preview position while the user drags the scrubber (-1 = not dragging).
    var previewPositionMs by remember { mutableLongStateOf(-1L) }
    val displayPositionMs = if (previewPositionMs >= 0L) previewPositionMs else positionMs

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = if (isFullscreen) 8.dp else 0.dp)
                .navigationBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatDuration(displayPositionMs)} / ${formatDuration(durationMs)}",
                style = textStyle,
                modifier =
                    Modifier
                        .padding(if (isFullscreen) 32.dp else 28.dp, 0.dp, 0.dp, 0.dp),
            )
            Box(
                modifier =
                    Modifier
                        .padding(0.dp, 0.dp, (if (isFullscreen) 32.dp else 28.dp) - 4.dp, 0.dp)
                        .tvFocus(shape = CircleShape, scale = 1.15f, borderColor = Color.White)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 24.dp),
                            onClick = onToggleFullscreen,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = stringResource(R.string.cd_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(if (isFullscreen) 28.dp else 24.dp),
                )
            }
        }

        SegmentedProgressSlider(
            positionMs = positionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            introRange = introRange,
            outroRange = outroRange,
            onSeek = { pos ->
                previewPositionMs = -1L
                onSeek(pos)
            },
            onSeekPreview = { pos ->
                previewPositionMs = pos
                // Keep the auto-hide timer at bay while the scrubber is being dragged.
                onInteraction()
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        if (isFullscreen) 32.dp else 28.dp,
                        0.dp,
                        if (isFullscreen) 32.dp else 28.dp,
                        0.dp,
                    ),
        )

        if (isFullscreen) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            if (isFullscreen) 32.dp else 28.dp,
                            0.dp,
                            if (isFullscreen) 32.dp else 28.dp,
                            0.dp,
                        ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onNextEpisode != null) {
                        Button(
                            onClick = onNextEpisode,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        Color.White.copy(
                                            alpha = 0.1f,
                                        ),
                                ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier =
                                Modifier
                                    // TV focus highlight (no-op on phones) — sibling overlay
                                    // of the player surface, so the graphicsLayer is safe.
                                    .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f, borderColor = Color.White)
                                    .height(36.dp),
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.player_next),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    IconButton(
                        onClick = onOpenEpisodes,
                        modifier = Modifier.tvFocus(shape = CircleShape, scale = 1.12f, borderColor = Color.White),
                    ) {
                        Icon(
                            Icons.Default.PlaylistPlay,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onOpenServers,
                        modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f, borderColor = Color.White),
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.streaming_server_short),
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f, borderColor = Color.White),
                    ) {
                        Icon(
                            Icons.Default.HighQuality,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            currentQuality ?: stringResource(R.string.player_quality_auto),
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f, borderColor = Color.White),
                    ) {
                        Text(
                            "${
                                if (currentSpeed ==
                                    currentSpeed
                                        .toInt()
                                        .toFloat()
                                ) {
                                    currentSpeed.toInt().toString()
                                } else {
                                    currentSpeed.toString()
                                }
                            }x",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
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
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pSize = if (isFullscreen) 16.dp else 0.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp + pSize)
                    .tvFocus(shape = CircleShape, scale = 1.12f, borderColor = Color.White)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 20.dp),
                        onClick = { onSeekRelative(-10000L) },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(24.dp + pSize / 2))
        }

        Spacer(modifier = Modifier.width(32.dp))

        Box(
            modifier = Modifier.size(56.dp + pSize),
            contentAlignment = Alignment.Center,
        ) {
            if (!isLoading) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp + pSize)
                            .tvFocus(shape = CircleShape, scale = 1.12f, borderColor = Color.White)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 28.dp),
                                onClick = { onPlayPause() },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp + pSize),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(32.dp))

        Box(
            modifier =
                Modifier
                    .size(40.dp + pSize)
                    .tvFocus(shape = CircleShape, scale = 1.12f, borderColor = Color.White)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 20.dp),
                        onClick = { onSeekRelative(10000L) },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(24.dp + pSize / 2))
        }
    }
}

@Composable
fun PlayerSideSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(320.dp),
                // IMPORTANT: no tonalElevation here — the app's colorScheme.primary is
                // AnimeRed, and M3 tints the surface toward the primary color at
                // elevation, which turned the whole fullscreen side panel dark red.
                color = SurfaceDark,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp, 8.dp),
                ) {
                    // Big header only when a title is provided — settings/subtitle
                    // sheets pass null and render their own per-pane header + back
                    // instead, so this row (title + close + divider) isn't duplicated.
                    if (title != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            IconButton(
                                onClick = onDismiss,
                                // TV focus highlight (no-op on phones) — the side panel is
                                // a sibling overlay of the player surface.
                                modifier = Modifier.tvFocus(shape = CircleShape, scale = 1.12f),
                            ) {
                                Icon(Icons.Default.Close, null, tint = TextSecondary)
                            }
                        }
                        HorizontalDivider(
                            color = CardBorderDark,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        content()
                    }
                }
            }
        }
    }
}
