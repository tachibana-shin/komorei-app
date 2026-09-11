package git.shin.komorei.ui.player

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.media3.exoplayer.ExoPlayer
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.Episode
import git.shin.komorei.model.SelectedFilter
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.ui.player.components.EpisodesContent
import git.shin.komorei.ui.player.components.ServerMenuContent
import git.shin.komorei.ui.theme.AnimeGreen
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import kotlin.math.roundToInt

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

    // Immersive fullscreen: hide the system bars while fullscreen, restore when leaving.
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

    // Swipe-to-adjust-brightness baseline: the window's current brightness, else
    // half (system default gives a negative value).
    val initialBrightness = (activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f })
        ?: 0.5f

    // Shared detail state — the same hiltViewModel instance AnimeDetailView uses —
    // feeds the in-player episode/season + server side menus and the next-episode
    // button without duplicating any fetch.
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

    // "Next episode" = the episode after the current one inside the selected season/chunk.
    val currentIndex = detailEpisodes.indexOfFirst { it.id == currentEp.id }
    val nextEpisode = detailEpisodes.getOrNull(currentIndex + 1)

    var activeMenu by remember { mutableStateOf<PlayerMenu?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeightPx = constraints.maxHeight.toFloat()
        val miniPlayerHeightPx =
            with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val bottomNavHeightPx =
            with(androidx.compose.ui.platform.LocalDensity.current) { bottomNavHeight.toPx() }

        // Max drag offset when collapsed: screen height minus (miniPlayerHeight + bottomNavHeight)
        val maxOffset = (fullHeightPx - miniPlayerHeightPx - bottomNavHeightPx).coerceAtLeast(0f)

        // Drag target offset based on sheet state
        val targetOffset = when (playbackState.sheetValue) {
            PlayerSheetValue.EXPANDED -> 0f
            PlayerSheetValue.COLLAPSED -> maxOffset
            PlayerSheetValue.HIDDEN -> fullHeightPx
        }

        var dragOffset by remember { mutableFloatStateOf(0f) }

        val animatedOffset by animateFloatAsState(
            targetValue = if (dragOffset != 0f) dragOffset else targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "SheetOffsetAnimation"
        )

        // Expansion fraction: 1.0f = fully EXPANDED, 0.0f = COLLAPSED
        val fraction = if (maxOffset > 0f) {
            (1f - (animatedOffset / maxOffset)).coerceIn(0f, 1f)
        } else 1f

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
                    onDragStopped = { velocity ->
                        // Decide state snap based on velocity or threshold
                        if (velocity > 800 || dragOffset > maxOffset * 0.4f) {
                            onStateChange(PlayerSheetValue.COLLAPSED)
                        } else if (velocity < -800 || dragOffset <= maxOffset * 0.4f) {
                            onStateChange(PlayerSheetValue.EXPANDED)
                        }
                        dragOffset = 0f
                    }
                )
                .testTag("video_player_sheet")
        ) {
            if (isCollapsed) {
                val progressFraction = if (playbackState.durationMs > 0) {
                    playbackState.currentPositionMs.toFloat() / playbackState.durationMs
                } else 0f

                // Mini Player pinned directly above bottom navigation bar
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
                        .border(
                            1.dp,
                            CardBorderDark,
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                )
            } else {
                // Expanded YouTube Style Player & Details Screen: a Column holds
                // the video + detail content; the slide-in side menus overlay the
                // whole sheet in a sibling Box so they never consume Column space
                // (a fillMaxSize() child inside the Column would starve the
                // weight(1f) video/detail strips down to 0 px — the "black below
                // the player" and "everything disappears in fullscreen" bugs).
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackgroundDark)
                    ) {
                    // Video Player Area: fills the whole sheet in fullscreen, otherwise
                    // a 16:9 strip pinned below the status bar/camera notch.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isFullscreen) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier
                                        .aspectRatio(16f / 9f)
                                        .statusBarsPadding()
                                }
                            )
                            .background(Color.Black)
                            .testTag("expanded_video_container")
                    ) {
                        PlayerVideoArea(
                            player = player,
                            title = anime.title,
                            episodeTitle = currentEp.title,
                            isPlaying = playbackState.isPlaying,
                            isFullscreen = isFullscreen,
                            currentPositionMs = playbackState.currentPositionMs,
                            durationMs = playbackState.durationMs,
                            currentSpeed = playbackState.playbackSpeed,
                            playbackError = playbackState.error,
                            initialBrightness = initialBrightness,
                            onMinimizeClick = {
                                if (isFullscreen) {
                                    onToggleFullscreen()
                                } else {
                                    onStateChange(PlayerSheetValue.COLLAPSED)
                                }
                            },
                            onToggleFullscreen = onToggleFullscreen,
                            onRefresh = onRetryStreams,
                            onSpeedChange = onSpeedChange,
                            onStartFastForward = onStartFastForward,
                            onStopFastForward = onStopFastForward,
                            onBrightnessChange = { level ->
                                activity?.window?.let { window ->
                                    window.attributes =
                                        window.attributes.apply { screenBrightness = level }
                                }
                            },
                            onOpenEpisodes = { activeMenu = PlayerMenu.EPISODES },
                            onOpenServers = { activeMenu = PlayerMenu.SERVERS },
                            onNextEpisode = nextEpisode?.let { ep -> { onEpisodeSelected(ep) } },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Loading / error overlay while a stream is being resolved.
                        if (playbackState.streamData == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    playbackState.isLoadingStreams -> CircularProgressIndicator(
                                        color = AnimeGreen,
                                        modifier = Modifier
                                            .testTag("stream_loading_indicator")
                                            .size(36.dp)
                                    )
                                    playbackState.streamError != null -> Text(
                                        text = playbackState.streamError.orEmpty(),
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Anime Detail Content (Title, Description, Episode selector, Related animes)
                    if (!isFullscreen) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    } // end Column
                    // In-player slide-in side menus (episode/season picker + server
                    // picker), overlaid on the whole sheet like the reference player.
                    // These are siblings of the Column in the Box above — NOT Column
                    // children — so their fillMaxSize() roots can't eat into the
                    // detail/video weight(1f) space.
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
                                effectiveSeasons.find { it.id == id }
                                    ?.let { detailViewModel.selectSeason(it) }
                            },
                            episodes = detailEpisodes,
                            episodesError = detailUiState.episodeError,
                            onRetryEpisodes = { detailViewModel.retryEpisodes() },
                            onEpisodeSelected = { ep ->
                                onEpisodeSelected(ep)
                                activeMenu = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    PlayerSideMenu(
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
                }
            }
        }
    }
}

/** Which in-player slide-in menu is open: episode/season picker or server picker. */
private enum class PlayerMenu { EPISODES, SERVERS }