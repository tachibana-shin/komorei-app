package git.shin.komorei.ui.player

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Episode
import git.shin.komorei.model.PlayerPlaybackState
import git.shin.komorei.model.PlayerSheetValue
import git.shin.komorei.model.SelectedFilter
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import kotlin.math.roundToInt

@Composable
fun VideoPlayerSheet(
    playbackState: PlayerPlaybackState,
    isBookmarked: Boolean,
    relatedAnimeList: List<Anime>,
    onStateChange: (PlayerSheetValue) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onToggleBookmark: () -> Unit,
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeightPx = constraints.maxHeight.toFloat()
        val miniPlayerHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val bottomNavHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { bottomNavHeight.toPx() }

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
                        .border(1.dp, CardBorderDark, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
            } else {
                // Expanded YouTube Style Player & Details Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    // Video Player Top Container with status bar padding so camera notch doesn't cover video
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .statusBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                                .testTag("expanded_video_container")
                        ) {
                            // Media3 ExoPlayer surface
                            Media3VideoPlayer(
                                videoUrl = currentEp.videoUrl,
                                isPlaying = playbackState.isPlaying,
                                onPositionChanged = { _, _, _ -> },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Custom Video Controller Overlay
                            CustomVideoControllerOverlay(
                                title = anime.title,
                                episodeTitle = currentEp.title,
                                isPlaying = playbackState.isPlaying,
                                currentPositionMs = playbackState.currentPositionMs,
                                durationMs = playbackState.durationMs,
                                onPlayPauseToggle = onPlayPauseToggle,
                                onSeekTo = onSeekTo,
                                onRewind10 = {
                                    onSeekTo((playbackState.currentPositionMs - 10000L).coerceAtLeast(0L))
                                },
                                onForward10 = {
                                    onSeekTo((playbackState.currentPositionMs + 10000L).coerceAtMost(playbackState.durationMs))
                                },
                                onMinimizeClick = {
                                    onStateChange(PlayerSheetValue.COLLAPSED)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Anime Detail Content (Title, Description, Episode selector, Related animes)
                    AnimeDetailView(
                        anime = anime,
                        currentEpisode = currentEp,
                        isBookmarked = isBookmarked,
                        relatedAnimeList = relatedAnimeList,
                        onEpisodeSelected = onEpisodeSelected,
                        onToggleBookmark = onToggleBookmark,
                        onAnimeSelected = onAnimeSelected,
                        onNavigateToCategory = onNavigateToCategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}
