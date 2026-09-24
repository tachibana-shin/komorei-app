package git.shin.komorei.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.Tracks
import git.shin.komorei.KomoreiApplication
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.Episode
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.ui.player.components.*
import git.shin.komorei.ui.theme.*
import git.shin.komorei.ui.tv.tvFocus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@SuppressLint("ContextCastToActivity")
@Composable
fun VideoPlayerSheet(
    playerViewModel: PlayerViewModel,
    onAnimeSelected: (Anime) -> Unit,
    onNavigateToCategory: (List<FilterValue>) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Height of the app's bottom navigation bar (px-converted inside). The collapsed
     * mini bubble's bottom corners are raised above it so it never covers the toolbar.
     */
    bottomToolbarPadding: Dp = 0.dp
) {
    // Everything derives from the ViewModel — the single source of truth for the
    // player engine, playback state and every action — so no callback plumbing.
    val playbackState by playerViewModel.playbackState.collectAsState()
    val player = playerViewModel.player
    // Per-anime related suggestions — re-fetched by the VM on every openAnime
    // (source's own `get_recommended_anime`, or a first-genre-tag search fallback).
    val relatedAnimeList by playerViewModel.relatedAnimeList.collectAsState()
    val onStateChange: (PlayerSheetValue) -> Unit = { playerViewModel.setPlayerSheetValue(it) }
    val onPlayPauseToggle = { playerViewModel.togglePlayPause() }
    val onToggleFullscreen = { playerViewModel.toggleFullscreen() }
    val onSpeedChange: (Float) -> Unit = { playerViewModel.setPlaybackSpeed(it) }
    val onStartFastForward = { playerViewModel.startFastForward() }
    val onStopFastForward = { playerViewModel.stopFastForward() }
    val onEpisodeSelected: (Episode) -> Unit = { playerViewModel.selectEpisode(it) }
    val onStreamSelected: (StreamInfo) -> Unit = { playerViewModel.selectStream(it) }
    val onRetryStreams = { playerViewModel.retryStreams() }
    val onToggleLock = { playerViewModel.toggleLock() }
    val onToggleSubtitles = { playerViewModel.toggleSubtitles() }
    val onTrackSelected: (Tracks.Group, Int) -> Unit = playerViewModel::selectTrack
    val onClearTrackType: (Int) -> Unit = playerViewModel::clearTrackType
    val onDismiss = { playerViewModel.dismissPlayer() }

    val anime = playbackState.currentAnime ?: return
    val currentEp = playbackState.currentEpisode ?: anime.episodes.firstOrNull() ?: return

    if (playbackState.sheetValue == PlayerSheetValue.HIDDEN) {
        return
    }

    val isFullscreen = playbackState.isFullscreen

    // Reliable Activity reference — KomoreiApplication tracks the current resumed
    // activity via ActivityLifecycleCallbacks (proven in PlayerViewModel).
    val activity = (LocalContext.current.applicationContext as? KomoreiApplication)?.currentActivity

    // Re-apply immersive bars after every recomposition in fullscreen. This catches
    // edge cases where the system restores bars (e.g. activity resume, notification
    // shade pull-down) without needing lifecycle API dependencies.
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, !isFullscreen)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (isFullscreen) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Keep screen on during playback; automatically cleared when the sheet is
    // dismissed (composable leaves composition → DisposableEffect disposed).
    val isPlaying = playbackState.isPlaying
    DisposableEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Live-effective brightness, seeded from the window's current value and mirrored on
    // every onBrightnessChange. Window attributes are plain fields, NOT Compose state —
    // setting them doesn't recompose the sheet, so a plain val would go stale and the next
    // brightness drag would base its delta on an old level (jumping to a fixed value
    // instead of moving with the swipe like the volume drag, which bases on the live
    // player.volume).
    var effectiveBrightness by remember {
        mutableStateOf((activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }) ?: 0.5f)
    }

    val detailViewModel: AnimeDetailViewModel = hiltViewModel()
    val detailUiState by detailViewModel.uiState.collectAsState()
    val watchHistory by detailViewModel.watchHistory.collectAsState()

    val menuAnime = detailUiState.masterAnime ?: anime
    val detailEpisodes = detailUiState.currentSeasonEpisodes
    val realSeasons = menuAnime.seasons.ifEmpty {
        listOf(AnimeSeason(menuAnime.id, stringResource(R.string.season_fallback_full)))
    }
    val effectiveSeasons = if (detailUiState.virtualSeasons.isEmpty()) {
        realSeasons
    } else {
        val parentId = detailUiState.selectedSeason?.animeId ?: menuAnime.id
        realSeasons.flatMap { season ->
            if (season.animeId == parentId) detailUiState.virtualSeasons else listOf(season)
        }
    }
    val menuSelectedSeasonId = detailUiState.selectedVirtualSeasonId
        ?: detailUiState.selectedSeason?.animeId
        ?: menuAnime.id

    val currentIndex = detailEpisodes.indexOfFirst { it.id == currentEp.id }
    val nextEpisode = detailEpisodes.getOrNull(currentIndex + 1)

    // SponsorBlock-style skip: while the playhead is inside the intro/outro range a
    // pill is shown; tapping it jumps to the end of the range. Dismissed per range per
    // episode, so it doesn't nag again after being skipped (e.g. user seeks back).
    var dismissedSkips by remember { mutableStateOf(setOf<SkipKind>()) }
    LaunchedEffect(currentEp.id) { dismissedSkips = emptySet() }
    val skipHint = remember(
        playbackState.currentPositionMs,
        playbackState.introRange,
        playbackState.outroRange,
        currentEp.id,
        dismissedSkips
    ) {
        val position = playbackState.currentPositionMs
        val outro = playbackState.outroRange
        val intro = playbackState.introRange
        when {
            outro != null && position in outro && SkipKind.OUTRO !in dismissedSkips ->
                SkipHint(SkipKind.OUTRO, outro.last)
            intro != null && position in intro && SkipKind.INTRO !in dismissedSkips ->
                SkipHint(SkipKind.INTRO, intro.last)
            else -> null
        }
    }
    val onSkipSegment: () -> Unit = {
        skipHint?.let { hint ->
            dismissedSkips = dismissedSkips + hint.kind
            playerViewModel.seekTo(hint.endMs + 1)
        }
        Unit
    }

    var activeMenu by remember { mutableStateOf<PlayerMenu?>(null) }

    // System back walks the player's own stack instead of finishing the activity:
    // close an open side menu → exit fullscreen → collapse to the mini player →
    // dismiss the player entirely. Only when the player is fully closed does back
    // reach the NavHost (pop tab / exit app). Composed whenever the sheet is visible
    // (the HIDDEN early-return above keeps this from intercepting back elsewhere).
    BackHandler {
        when {
            activeMenu != null -> activeMenu = null
            isFullscreen -> onToggleFullscreen()
            playbackState.sheetValue == PlayerSheetValue.EXPANDED ->
                onStateChange(PlayerSheetValue.COLLAPSED)
            else -> onDismiss()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        // Single source of truth for the sheet's vertical position (px). A drag snaps it
        // 1:1 to the finger; on release it springs toward the target while KEEPING the
        // finger's release velocity (momentum) — the old animateFloatAsState restarted the
        // spring from rest on every release, so the sheet visibly "khựng" (dead-stop start)
        // instead of gliding like YouTube. Damping CRITICAL (1.0) never overshoots: an
        // underdamped 0.9 bounced past fullHeight, pushed `fraction` back over the bubble
        // gate and ping-ponged the shared video surface sheet↔bubble (black flicker).
        val fullHeightPx = constraints.maxHeight.toFloat()

        val targetOffset = when (playbackState.sheetValue) {
            PlayerSheetValue.EXPANDED -> 0f
            PlayerSheetValue.COLLAPSED, PlayerSheetValue.HIDDEN -> fullHeightPx
        }

        val sheetOffset = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        var isSheetDragging by remember { mutableStateOf(false) }
        var releaseVelocity by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(targetOffset, isSheetDragging, isFullscreen) {
            if (!isSheetDragging && !isFullscreen) {
                val startVelocity = releaseVelocity
                releaseVelocity = 0f
                sheetOffset.animateTo(
                    targetValue = targetOffset,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialVelocity = startVelocity
                )
            }
        }

        // ~0 = fully expanded → ~1 = fully collapsed (off-screen). The collapse combines
        // a 1:1 SLIDE — the whole sheet Box follows the finger via its layout offset
        // below ("trượt theo nhịp vuốt") — with a FADE: a black veil inside the column
        // dims the content (video + detail + controls) progressively, so the player
        // "fade mờ ẩn hẳn" as it slides away. NO graphicsLayer on the video path — a
        // TextureView under any layered ancestor renders BLACK (AGENTS.md); the fade is
        // a plain opaque overlay, not an alpha on the surface.
        val collapse = if (fullHeightPx > 0f) (sheetOffset.value / fullHeightPx).coerceIn(0f, 1f) else 0f
        val showBubble = playbackState.sheetValue == PlayerSheetValue.COLLAPSED && collapse >= 0.98f && !isFullscreen
        // Mutual exclusion: the engine can only paint to ONE surface (media3 keeps a
        // single videoSurface — a second attach replaces it, and detaching the bubble
        // would clearVideoSurface and leave the sheet's view black on expand). So the
        // sheet's video area is uncomposed exactly while the floating bubble is up.
        val showSheetVideo = !showBubble

        // Drag math: raw finger deltas accumulate on the POSITION SET AT THE MOMENT OF
        // DRAG START (the Animatable is stopped there). Deliberately never feed an
        // animated value back into the accumulator — that compounded a slightly-lagging
        // value every step and the sheet crept (see round-1 fix notes).
        val draggableState = rememberDraggableState { delta ->
            scope.launch { sheetOffset.snapTo((sheetOffset.value + delta).coerceIn(0f, fullHeightPx)) }
        }

        // Mini bubble geometry (px) — shared with FloatingMiniPlayer: size, the four
        // corner anchors (bottom ones raised above the app's bottom toolbar) and the
        // bottom-center point the bubble appears from. The bubble's top-left is hoisted
        // here so it keeps whatever corner the user snapped it to across collapse/expand.
        val bottomInsetPx = with(LocalDensity.current) { bottomToolbarPadding.toPx() }.coerceAtLeast(0f)
        val miniGeometry = computeMiniPlayerGeometry(
            maxWidthPx = constraints.maxWidth.toFloat(),
            maxHeightPx = fullHeightPx,
            density = LocalDensity.current,
            bottomInsetPx = bottomInsetPx
        )
        var miniBubblePos: Offset by remember { mutableStateOf(miniGeometry.cornerBottomRight) }
        // Pivot for the bubble's scaleIn enter animation: the corner the bubble will
        // occupy, as a fraction of this overlay. The bubble visibly GROWS OUT of the
        // corner it lands in (plus a short fade) — "mở mini player bằng animate" —
        // instead of scaling around the screen center.
        val bubbleOrigin = TransformOrigin(
            (miniBubblePos.x / constraints.maxWidth.coerceAtLeast(1)).coerceIn(0f, 1f),
            (miniBubblePos.y / constraints.maxHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The WHOLE sheet slides down 1:1 with the finger via a plain LAYOUT
                // offset ("trượt theo nhịp vuốt") — placement-only, and NOT graphicsLayer
                // (this box contains the TextureView surface; layering it blacks the
                // video). At full collapse it sits fully below the viewport, so the app
                // behind gets the pointer and the mini bubble takes over.
                .offset { IntOffset(x = 0, y = sheetOffset.value.roundToInt()) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    // Drag only while the sheet is up. Once collapsed the overlay must
                    // NOT swallow touches — the mini bubble is the interactive element
                    // and the app behind it needs the pointer (fullscreen locks too).
                    enabled = !isFullscreen && playbackState.sheetValue != PlayerSheetValue.COLLAPSED,
                    onDragStarted = {
                        isSheetDragging = true
                        scope.launch { sheetOffset.stop() }
                    },
                    onDragStopped = { velocity ->
                        isSheetDragging = false
                        releaseVelocity = velocity
                        val endOffset = sheetOffset.value
                        if (velocity > 800 || endOffset > fullHeightPx * 0.4f) {
                            onStateChange(PlayerSheetValue.COLLAPSED)
                        } else if (velocity < -800 || endOffset <= fullHeightPx * 0.4f) {
                            onStateChange(PlayerSheetValue.EXPANDED)
                        }
                    }
                )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            // Opaque wall behind the sheet content. It rides along with the
                            // whole sheet's offset and leaves the viewport entirely when
                            // collapsed, so it never lingers over the app behind the bubble.
                            .background(BackgroundDark)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isFullscreen) Modifier.weight(1f)
                                    else Modifier.aspectRatio(16f / 9f).statusBarsPadding()
                                )
                                // Opaque black behind the video. NO offset, NO graphicsLayer —
                                // the whole sheet Box slides via its own layout offset above,
                                // and the fade comes from the veil overlay below.
                                .background(Color.Black)
                        ) {
                            if (showSheetVideo) {
                                PlayerVideoArea(
                                player = player,
                                title = anime.title,
                                episodeTitle = episodeHeaderLabel(currentEp.episodeNumber, currentEp.title),
                                posterUrl = anime.posterUrl,
                                isPlaying = playbackState.isPlaying,
                                isLoading = playbackState.isLoading,
                                isFullscreen = isFullscreen,
                                currentPositionMs = playbackState.currentPositionMs,
                                durationMs = playbackState.durationMs,
                                bufferedPositionMs = playbackState.bufferedPositionMs,
                                currentSpeed = playbackState.playbackSpeed,
                                currentQuality = playbackState.streams.find { it.id == playbackState.selectedStreamId }?.name,
                                isSubtitleEnabled = playbackState.isSubtitleEnabled,
                                introRange = playbackState.introRange,
                                outroRange = playbackState.outroRange,
                                streamData = playbackState.streamData,
                                playbackError = playbackState.error,
                                initialBrightness = effectiveBrightness,
                                isLocked = playbackState.isLocked,
                                onMinimizeClick = {
                                    if (isFullscreen) onToggleFullscreen()
                                    else onStateChange(PlayerSheetValue.COLLAPSED)
                                },
                                onToggleFullscreen = onToggleFullscreen,
                                onRefresh = onRetryStreams,
                                onSpeedChange = onSpeedChange,
                                onStartFastForward = onStartFastForward,
                                onStopFastForward = onStopFastForward,
                                onBrightnessChange = { level ->
                                    effectiveBrightness = level
                                    activity?.window?.let { window ->
                                        window.attributes = window.attributes.apply { screenBrightness = level }
                                    }
                                },
                                onToggleLock = onToggleLock,
                                onToggleSubtitles = onToggleSubtitles,
                                onOpenSubtitleMenu = { activeMenu = PlayerMenu.SUBTITLES },
                                onOpenSettings = { activeMenu = PlayerMenu.SETTINGS },
                                onOpenEpisodes = { activeMenu = PlayerMenu.EPISODES },
                                onOpenServers = { activeMenu = PlayerMenu.SERVERS },
                                onNextEpisode = nextEpisode?.let { ep -> { onEpisodeSelected(ep) } },
                                skipHint = skipHint,
                                onSkip = onSkipSegment,
                                modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Loading / Error indicator inside the video area container but above the player surface
                            val streamError = playbackState.streamError
                            if (playbackState.streamData == null && !playbackState.isLocked && streamError != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = streamError,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                        Button(
                                            onClick = onRetryStreams,
                                            colors = ButtonDefaults.buttonColors(containerColor = AnimeRed),
                                            // TV focus highlight (no-op on phones). The retry
                                            // floats over the player surface as a sibling Box —
                                            // never an ancestor of the TextureView, so the
                                            // graphicsLayer is safe.
                                            modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                                        ) {
                                            Text(stringResource(R.string.action_retry))
                                        }
                                    }
                                }
                            }
                        }

                        // Anime Detail Content (Title, Description, Episode selector, Related animes)
                        // Always keep in composition to preserve scroll state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isFullscreen) Modifier.height(0.dp)
                                    else Modifier.weight(1f)
                                )
                        ) {
                            AnimeDetailView(
                                anime = anime,
                                currentEpisode = currentEp,
                                relatedAnimeList = relatedAnimeList,
                                streams = playbackState.streams,
                                selectedStreamId = playbackState.selectedStreamId,
                                isLoadingStreams = playbackState.isLoadingStreams,
                                streamError = playbackState.streamError,
                                onEpisodeSelected = onEpisodeSelected,
                                onStreamSelected = onStreamSelected,
                                onRetryStreams = onRetryStreams,
                                onAnimeSelected = onAnimeSelected,
                                onNavigateToCategory = onNavigateToCategory,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Fade-to-black veil ("fade mờ"): dims the whole sheet content
                    // progressively as the drag/slide progresses. A plain opaque overlay
                    // over the Column — the ONLY way to "fade" a TextureView surface,
                    // which would render BLACK if any ancestor grew a graphicsLayer
                    // alpha instead (AGENTS.md). MUST be a sibling overlay of this Box,
                    // NOT a Column child — a full-size child inside the Column starves
                    // the weighted detail box down to 0px ("content bị xóa, chỉ còn nền").
                    // Off while the mini bubble is up (the sheet is fully off-screen then).
                    if (collapse > 0f && !showBubble) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = collapse))
                        )
                    }

                    // Side Sheets for Fullscreen
                    if (isFullscreen) {
                        PlayerSideSheet(
                            visible = activeMenu == PlayerMenu.EPISODES,
                            onDismiss = { activeMenu = null },
                            title = stringResource(R.string.cd_episodes_list)
                        ) {
                            EpisodesContent(
                                anime = menuAnime,
                                seasons = effectiveSeasons,
                                currentEpisode = currentEp,
                                watchHistory = watchHistory,
                                selectedSeasonId = menuSelectedSeasonId,
                                onSeasonChange = { id ->
                                    effectiveSeasons.find { it.id == id }?.let { detailViewModel.selectSeason(it) }
                                },
                                episodes = detailEpisodes,
                                episodesError = detailUiState.episodeError,
                                isLoading = detailUiState.isLoadingEpisodes,
                                onRetryEpisodes = { detailViewModel.retryEpisodes() },
                                onEpisodeSelected = { ep ->
                                    onEpisodeSelected(ep)
                                    activeMenu = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        PlayerSideSheet(
                            visible = activeMenu == PlayerMenu.SERVERS,
                            onDismiss = { activeMenu = null },
                            title = stringResource(R.string.streaming_server_header)
                        ) {
                            ServerMenuContent(
                                streams = playbackState.streams,
                                selectedStreamId = playbackState.selectedStreamId,
                                isLoading = playbackState.isLoadingStreams,
                                onStreamSelected = { stream ->
                                    onStreamSelected(stream)
                                    activeMenu = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        PlayerSideSheet(
                            visible = activeMenu == PlayerMenu.SETTINGS,
                            onDismiss = { activeMenu = null }
                        ) {
                            UnifiedSettingsContent(
                                playbackState = playbackState,
                                autoNextEnabled = playbackState.autoNextEnabled,
                                onAutoNextChange = { playerViewModel.setAutoNextEnabled(it) },
                                onSpeedChange = onSpeedChange,
                                onStreamSelected = onStreamSelected,
                                onTrackSelected = onTrackSelected,
                                onClearTrackType = onClearTrackType,
                                onDismiss = { activeMenu = null }
                            )
                        }

                        PlayerSideSheet(
                            visible = activeMenu == PlayerMenu.SUBTITLES,
                            onDismiss = { activeMenu = null }
                        ) {
                            TrackSelectionPane(
                                title = stringResource(R.string.player_subtitle),
                                type = androidx.media3.common.C.TRACK_TYPE_TEXT,
                                availableTracks = playbackState.availableTracks,
                                onTrackSelected = { group, index ->
                                    onTrackSelected(group, index)
                                    activeMenu = null
                                },
                                onClearTrack = {
                                    onClearTrackType(androidx.media3.common.C.TRACK_TYPE_TEXT)
                                    activeMenu = null
                                },
                                onBack = { activeMenu = PlayerMenu.SETTINGS }
                            )
                        }
                    } else {
                        // Bottom Sheets for Normal Mode
                        if (activeMenu == PlayerMenu.SETTINGS) {
                            UnifiedPlayerSettingsSheet(
                                playbackSpeed = playbackState.playbackSpeed,
                                availableTracks = playbackState.availableTracks,
                                streams = playbackState.streams,
                                selectedStreamId = playbackState.selectedStreamId,
                                videoTrackOverride = playbackState.videoTrackOverride,
                                videoSize = playbackState.videoSize,
                                autoNextEnabled = playbackState.autoNextEnabled,
                                onAutoNextChange = { playerViewModel.setAutoNextEnabled(it) },
                                onSpeedChange = onSpeedChange,
                                onStreamSelected = onStreamSelected,
                                onTrackSelected = onTrackSelected,
                                onClearTrackType = onClearTrackType,
                                onDismiss = { activeMenu = null }
                            )
                        }

                        PlayerSideMenu(
                            visible = activeMenu == PlayerMenu.EPISODES,
                            onDismiss = { activeMenu = null },
                            title = stringResource(R.string.cd_episodes_list)
                        ) {
                            EpisodesContent(
                                anime = menuAnime,
                                seasons = effectiveSeasons,
                                currentEpisode = currentEp,
                                watchHistory = watchHistory,
                                selectedSeasonId = menuSelectedSeasonId,
                                onSeasonChange = { id ->
                                    effectiveSeasons.find { it.id == id }?.let { detailViewModel.selectSeason(it) }
                                },
                                episodes = detailEpisodes,
                                episodesError = detailUiState.episodeError,
                                isLoading = detailUiState.isLoadingEpisodes,
                                onRetryEpisodes = { detailViewModel.retryEpisodes() },
                                onEpisodeSelected = { ep ->
                                    onEpisodeSelected(ep)
                                    activeMenu = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
            }
        }

        // Floating PiP-style bubble — MUST be a SIBLING of the offset sheet (a child
        // of the sheet Box would be shifted off-screen along with it, exactly the
        // "mini player bị đá ra khỏi màn hình" symptom). Free-dragged like a chat
        // bubble; gated on the sheet being essentially fully off-screen
        // (collapse ≈ 1) so the shared engine never paints two ComposePlayer surfaces.
        // Sequence per UX: the player fades/slides away COMPLETELY ("ẩn hẳn") and only
        // THEN the mini player OPENS with its own enter animation ("mở mini player bằng
        // animate") — a scale-in pivoted at the corner it occupies, plus a short fade.
        // The bubble's position is HOISTED (miniBubblePos) so it reopens in whatever
        // corner the user left it.
        AnimatedVisibility(
            visible = showBubble,
            enter = scaleIn(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                initialScale = 0.5f,
                transformOrigin = bubbleOrigin
            ) + fadeIn(animationSpec = tween(180, delayMillis = 90)),
            exit = fadeOut(animationSpec = tween(120)) + scaleOut(
                animationSpec = tween(150),
                targetScale = 0.8f,
                transformOrigin = bubbleOrigin
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            FloatingMiniPlayer(
                player = player,
                posterUrl = anime.posterUrl,
                isPlaying = playbackState.isPlaying,
                initialPosition = miniBubblePos,
                onPositionChange = { miniBubblePos = it },
                onExpand = { onStateChange(PlayerSheetValue.EXPANDED) },
                onPlayPauseToggle = onPlayPauseToggle,
                onClose = onDismiss,
                modifier = Modifier.fillMaxSize(),
                bottomInsetPx = bottomInsetPx
            )
        }
    }
}

@Composable
fun UnifiedSettingsContent(
    playbackState: PlayerPlaybackState,
    autoNextEnabled: Boolean,
    onAutoNextChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStreamSelected: (StreamInfo) -> Unit,
    onTrackSelected: (Tracks.Group, Int) -> Unit,
    onClearTrackType: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsContent(
        playbackState = playbackState,
        autoNextEnabled = autoNextEnabled,
        onAutoNextChange = onAutoNextChange,
        onSpeedChange = onSpeedChange,
        onStreamSelected = onStreamSelected,
        onTrackSelected = onTrackSelected,
        onClearTrackType = onClearTrackType,
        onDismiss = onDismiss
    )
}

/**
 * Player header episode line: "Tập {episodeNumber}" or "Tập {episodeNumber} - {title}".
 * Some sources title episodes with just the bare number ("2") or a redundant
 * "Tập N" label — those suffixes are skipped so the line reads clean.
 */
@Composable
private fun episodeHeaderLabel(episodeNumber: String, title: String): String {
    val detail = title.trim().takeIf { it.isNotEmpty() && it != episodeNumber }
    return if (detail != null) {
        stringResource(R.string.episode_title_format, episodeNumber, detail)
    } else {
        stringResource(R.string.episode_format, episodeNumber)
    }
}

private enum class PlayerMenu { EPISODES, SERVERS, SETTINGS, SUBTITLES }
