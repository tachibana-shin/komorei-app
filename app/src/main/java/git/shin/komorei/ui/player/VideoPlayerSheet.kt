package git.shin.komorei.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.Episode
import git.shin.komorei.model.SelectedFilter
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.ui.player.components.*
import git.shin.komorei.ui.theme.*
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@SuppressLint("ContextCastToActivity")
@Composable
fun VideoPlayerSheet(
    playbackState: PlayerPlaybackState,
    player: ExoPlayer,
    relatedAnimeList: List<Anime>,
    onStateChange: (PlayerSheetValue) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStartFastForward: () -> Unit,
    onStopFastForward: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onEpisodeSelected: (Episode) -> Unit,
    onStreamSelected: (StreamInfo) -> Unit,
    onRetryStreams: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onResizeModeChange: (Int) -> Unit,
    onTrackSelected: (Tracks.Group, Int) -> Unit,
    onClearTrackType: (Int) -> Unit,
    onDismiss: () -> Unit,
    onAnimeSelected: (Anime) -> Unit,
    onNavigateToCategory: (List<SelectedFilter>) -> Unit,
    bottomNavHeight: androidx.compose.ui.unit.Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    val anime = playbackState.currentAnime ?: return
    val currentEp = playbackState.currentEpisode ?: anime.episodes.firstOrNull() ?: return

    if (playbackState.sheetValue == PlayerSheetValue.HIDDEN) {
        return
    }

    val isFullscreen = playbackState.isFullscreen

    // Immersive fullscreen
    val activity = LocalContext.current as? Activity
    LaunchedEffect(isFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
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

    val initialBrightness = (activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }) ?: 0.5f

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

    var activeMenu by remember { mutableStateOf<PlayerMenu?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeightPx = constraints.maxHeight.toFloat()
        val miniPlayerHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val bottomNavHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { bottomNavHeight.toPx() }
        val maxOffset = (fullHeightPx - miniPlayerHeightPx - bottomNavHeightPx).coerceAtLeast(0f)

        val targetOffset = when (playbackState.sheetValue) {
            PlayerSheetValue.EXPANDED -> 0f
            PlayerSheetValue.COLLAPSED -> maxOffset
            PlayerSheetValue.HIDDEN -> fullHeightPx
        }

        var dragOffset by remember { mutableFloatStateOf(0f) }
        val animatedOffset by animateFloatAsState(
            targetValue = if (dragOffset != 0f) dragOffset else targetOffset,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "SheetOffsetAnimation"
        )

        val fraction = if (maxOffset > 0f) (1f - (animatedOffset / maxOffset)).coerceIn(0f, 1f) else 1f
        val isCollapsed = fraction < 0.2f

        val draggableState = rememberDraggableState { delta ->
            val newOffset = (animatedOffset + delta).coerceIn(0f, maxOffset)
            dragOffset = newOffset
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(x = 0, y = animatedOffset.roundToInt()) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    enabled = !isFullscreen, // Disable dragging the sheet while in fullscreen
                    onDragStopped = { velocity ->
                        if (velocity > 800 || dragOffset > maxOffset * 0.4f) {
                            onStateChange(PlayerSheetValue.COLLAPSED)
                        } else if (velocity < -800 || dragOffset <= maxOffset * 0.4f) {
                            onStateChange(PlayerSheetValue.EXPANDED)
                        }
                        dragOffset = 0f
                    }
                )
        ) {
            if (isCollapsed) {
                val progressFraction = if (playbackState.durationMs > 0) {
                    playbackState.currentPositionMs.toFloat() / playbackState.durationMs
                } else 0f

                MiniPlayer(
                    anime = anime,
                    episode = currentEp,
                    isPlaying = playbackState.isPlaying,
                    progressFraction = progressFraction,
                    onExpand = { onStateChange(PlayerSheetValue.EXPANDED) },
                    onPlayPauseToggle = onPlayPauseToggle,
                    onClose = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .border(1.dp, CardBorderDark, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isFullscreen) Modifier.weight(1f)
                                    else Modifier.aspectRatio(16f / 9f).statusBarsPadding()
                                )
                                .background(Color.Black)
                        ) {
                            PlayerVideoArea(
                                player = player,
                                title = anime.title,
                                episodeTitle = currentEp.title,
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
                                initialBrightness = initialBrightness,
                                isLocked = playbackState.isLocked,
                                videoResizeMode = playbackState.videoResizeMode,
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
                                modifier = Modifier.fillMaxSize()
                            )

                            // Loading / Error indicator inside the video area container but above the player surface
                            if (playbackState.streamData == null && !playbackState.isLocked && playbackState.streamError != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = playbackState.streamError,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                        Button(
                                            onClick = onRetryStreams,
                                            colors = ButtonDefaults.buttonColors(containerColor = AnimeRed)
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
                                    if (isFullscreen) Modifier.height(0.dp).alpha(0f)
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
                                onSpeedChange = onSpeedChange,
                                onResizeModeChange = onResizeModeChange,
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
                                videoResizeMode = playbackState.videoResizeMode,
                                availableTracks = playbackState.availableTracks,
                                streams = playbackState.streams,
                                selectedStreamId = playbackState.selectedStreamId,
                                onSpeedChange = onSpeedChange,
                                onResizeModeChange = onResizeModeChange,
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
        }
    }
}

@Composable
fun UnifiedSettingsContent(
    playbackState: PlayerPlaybackState,
    onSpeedChange: (Float) -> Unit,
    onResizeModeChange: (Int) -> Unit,
    onStreamSelected: (StreamInfo) -> Unit,
    onTrackSelected: (Tracks.Group, Int) -> Unit,
    onClearTrackType: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsContent(
        playbackState = playbackState,
        onSpeedChange = onSpeedChange,
        onResizeModeChange = onResizeModeChange,
        onStreamSelected = onStreamSelected,
        onTrackSelected = onTrackSelected,
        onClearTrackType = onClearTrackType,
        onDismiss = onDismiss
    )
}

private enum class PlayerMenu { EPISODES, SERVERS, SETTINGS, SUBTITLES }
