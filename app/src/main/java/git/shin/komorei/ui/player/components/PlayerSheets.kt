package git.shin.komorei.ui.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Tracks
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.model.*
import git.shin.komorei.ui.player.PlayerPlaybackState
import git.shin.komorei.ui.components.EpisodeProgressBar
import git.shin.komorei.ui.theme.*
import git.shin.komorei.ui.utils.animateScrollToItemCentered
import git.shin.komorei.ui.utils.scrollToItemVisible

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesBottomSheet(
    anime: Anime,
    seasons: List<AnimeSeason>,
    currentEpisode: Episode,
    watchHistory: List<WatchHistory>,
    selectedSeasonId: String,
    episodes: List<Episode>,
    episodesError: String?,
    onRetryEpisodes: () -> Unit,
    onSeasonChange: (String) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        contentColor = TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardBorderDark)
            )
        }
    ) {
        EpisodesContent(
            anime = anime,
            seasons = seasons,
            currentEpisode = currentEpisode,
            watchHistory = watchHistory,
            selectedSeasonId = selectedSeasonId,
            onSeasonChange = onSeasonChange,
            episodes = episodes,
            episodesError = episodesError,
            onRetryEpisodes = onRetryEpisodes,
            onEpisodeSelected = onEpisodeSelected,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("episodes_full_bottom_sheet")
        )
    }
}

@Composable
fun EpisodesContent(
    anime: Anime,
    seasons: List<AnimeSeason>,
    currentEpisode: Episode,
    watchHistory: List<WatchHistory>,
    selectedSeasonId: String,
    episodes: List<Episode>,
    episodesError: String?,
    onRetryEpisodes: () -> Unit,
    onSeasonChange: (String) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    var isGridView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isAscending by remember { mutableStateOf(true) }
    var showSeasonList by remember { mutableStateOf(false) }

    val filteredEpisodes = remember(episodes, searchQuery, isAscending) {
        val list = if (searchQuery.isBlank()) {
            episodes
        } else {
            episodes.filter { ep ->
                ep.episodeNumber.contains(searchQuery.trim()) ||
                        ep.title.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
        if (isAscending) list else list.reversed()
    }

    Column(modifier = modifier) {
        SeasonVsEpisodePane(
            modifier = Modifier.weight(1f),
            showSeasonList = showSeasonList,
            seasons = seasons,
            selectedSeasonId = selectedSeasonId,
            onSeasonChange = onSeasonChange,
            onCloseSeasonList = { showSeasonList = false }
        ) {
            if (seasons.isNotEmpty()) {
                val seasonsRowState = rememberLazyListState()
                val activeSeasonIndex = seasons.indexOfFirst { it.id == selectedSeasonId }
                LaunchedEffect(selectedSeasonId, seasons.size) {
                    seasonsRowState.animateScrollToItemCentered(activeSeasonIndex)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        state = seasonsRowState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(seasons) { season ->
                            val isSelected = season.id == selectedSeasonId
                            Surface(
                                onClick = { onSeasonChange(season.id) },
                                color = if (isSelected) AnimeRedContainer else SurfaceDark,
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) AnimeRed else CardBorderDark
                                ),
                                modifier = Modifier.testTag("sheet_season_tab_${season.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) AnimeRed else Color.Transparent)
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = season.title,
                                        color = if (isSelected) AnimeRed else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = { showSeasonList = true },
                        color = SurfaceDark,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.cd_season_list),
                                tint = TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                            cursorBrush = SolidColor(AnimeRed),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxHeight(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.episode_search_hint),
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                Surface(
                    onClick = { isAscending = !isAscending },
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.cd_sort),
                            tint = if (isAscending) TextSecondary else AnimeRed,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Surface(
                    onClick = { isGridView = !isGridView },
                    color = if (isGridView) AnimeRedContainer else SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isGridView) AnimeRed else CardBorderDark
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = stringResource(R.string.cd_toggle_view),
                            tint = if (isGridView) AnimeRed else TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            if (isGridView) {
                val gridState = rememberLazyGridState()
                val activeGridIndex = filteredEpisodes.indexOfFirst { it.id == currentEpisode.id }
                LaunchedEffect(activeGridIndex) {
                    if (activeGridIndex >= 0) gridState.scrollToItemVisible(activeGridIndex)
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredEpisodes) { ep ->
                        val isPlaying = ep.id == currentEpisode.id
                        val history = watchHistory.find { it.episodeId == ep.id }

                        Surface(
                            onClick = { onEpisodeSelected(ep) },
                            color = if (isPlaying) AnimeRedContainer else SurfaceDark,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPlaying) AnimeRed else CardBorderDark
                            ),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ep.episodeNumber,
                                    color = if (isPlaying) AnimeRed else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val activeIndex = filteredEpisodes.indexOfFirst { it.id == currentEpisode.id }
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) listState.scrollToItemVisible(activeIndex)
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredEpisodes) { ep ->
                        val isPlaying = ep.id == currentEpisode.id
                        val history = watchHistory.find { it.episodeId == ep.id }
                        EpisodeListItemCard(
                            episode = ep,
                            posterUrl = anime.posterUrl,
                            isPlaying = isPlaying,
                            progress = history?.progressFraction ?: 0f,
                            onClick = { onEpisodeSelected(ep) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonVsEpisodePane(
    modifier: Modifier = Modifier,
    showSeasonList: Boolean,
    seasons: List<AnimeSeason>,
    selectedSeasonId: String,
    onSeasonChange: (String) -> Unit,
    onCloseSeasonList: () -> Unit,
    episodesContent: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = showSeasonList,
            enter = fadeIn(animationSpec = tween(200)) + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            SeasonPickerPane(
                seasons = seasons,
                selectedSeasonId = selectedSeasonId,
                onSeasonChange = onSeasonChange,
                onClose = onCloseSeasonList
            )
        }
        AnimatedVisibility(
            visible = !showSeasonList,
            enter = fadeIn(animationSpec = tween(200)) + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                episodesContent()
            }
        }
    }
}

@Composable
private fun SeasonPickerPane(
    seasons: List<AnimeSeason>,
    selectedSeasonId: String,
    onSeasonChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.season_picker_title, seasons.size),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, null, tint = TextSecondary)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(seasons) { season ->
                val isSelected = season.id == selectedSeasonId
                Surface(
                    onClick = {
                        onSeasonChange(season.id)
                        onClose()
                    },
                    color = if (isSelected) AnimeRedContainer else SurfaceDark,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AnimeRed else CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = season.title,
                            color = if (isSelected) AnimeRed else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = AnimeRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeListItemCard(
    episode: Episode,
    posterUrl: String,
    isPlaying: Boolean,
    progress: Float = 0f,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isPlaying) AnimeRedContainer.copy(alpha = 0.5f) else SurfaceDark,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPlaying) AnimeRed else CardBorderDark
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BackgroundDark)
            ) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPlaying) AnimeRed else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (progress > 0f) {
                    EpisodeProgressBar(
                        progress = progress,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    color = if (isPlaying) AnimeRed else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.quality,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun SettingsContent(
    playbackState: PlayerPlaybackState,
    onSpeedChange: (Float) -> Unit,
    onResizeModeChange: (Int) -> Unit,
    onStreamSelected: (StreamInfo) -> Unit,
    onTrackSelected: (Tracks.Group, Int) -> Unit,
    onClearTrackType: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPane by remember { mutableStateOf(SettingsPane.MAIN) }

    AnimatedContent(
        targetState = currentPane,
        transitionSpec = {
            if (targetState.ordinal > initialState.ordinal) {
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
            } else {
                (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "SettingsPaneTransition"
    ) { pane ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            when (pane) {
                SettingsPane.MAIN -> {
                    Text(
                        text = stringResource(R.string.player_settings_unified),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(16.dp, 8.dp)
                    )
                    SettingsItem(
                        icon = Icons.Default.Speed,
                        title = stringResource(R.string.player_speed_title),
                        value = "${if (playbackState.playbackSpeed == playbackState.playbackSpeed.toInt().toFloat()) playbackState.playbackSpeed.toInt().toString() else playbackState.playbackSpeed.toString()}x",
                        onClick = { currentPane = SettingsPane.SPEED }
                    )
                    SettingsItem(
                        icon = Icons.Default.AspectRatio,
                        title = stringResource(R.string.player_aspect_ratio),
                        value = getResizeModeLabel(playbackState.videoResizeMode),
                        onClick = { currentPane = SettingsPane.ASPECT_RATIO }
                    )
                    SettingsItem(
                        icon = Icons.Default.HighQuality,
                        title = stringResource(R.string.player_quality),
                        value = playbackState.streams.find { it.id == playbackState.selectedStreamId }?.name ?: stringResource(R.string.unknown),
                        onClick = { currentPane = SettingsPane.QUALITY }
                    )
                    SettingsItem(
                        icon = Icons.Default.Audiotrack,
                        title = stringResource(R.string.player_audio),
                        value = getSelectedTrackLabel(playbackState.availableTracks, C.TRACK_TYPE_AUDIO),
                        onClick = { currentPane = SettingsPane.AUDIO }
                    )
                    SettingsItem(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.player_subtitle),
                        value = getSelectedTrackLabel(playbackState.availableTracks, C.TRACK_TYPE_TEXT),
                        onClick = { currentPane = SettingsPane.SUBTITLE }
                    )
                }
                SettingsPane.SPEED -> {
                    PaneHeader(stringResource(R.string.player_speed_title), onBack = { currentPane = SettingsPane.MAIN })
                    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                    LazyColumn {
                        items(speeds) { speed ->
                            SelectableItem(
                                label = "${if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()}x",
                                isSelected = speed == playbackState.playbackSpeed,
                                onClick = {
                                    onSpeedChange(speed)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
                SettingsPane.ASPECT_RATIO -> {
                    PaneHeader(stringResource(R.string.player_aspect_ratio), onBack = { currentPane = SettingsPane.MAIN })
                    val modes = listOf(0, 3, 4) // Fit, Fill, Zoom
                    LazyColumn {
                        items(modes) { mode ->
                            SelectableItem(
                                label = getResizeModeLabel(mode),
                                isSelected = mode == playbackState.videoResizeMode,
                                onClick = {
                                    onResizeModeChange(mode)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
                SettingsPane.QUALITY -> {
                    PaneHeader(stringResource(R.string.player_quality), onBack = { currentPane = SettingsPane.MAIN })
                    LazyColumn {
                        items(playbackState.streams) { stream ->
                            SelectableItem(
                                label = stream.name,
                                isSelected = stream.id == playbackState.selectedStreamId,
                                onClick = {
                                    onStreamSelected(stream)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
                SettingsPane.AUDIO -> {
                    TrackSelectionPane(
                        title = stringResource(R.string.player_audio),
                        type = C.TRACK_TYPE_AUDIO,
                        availableTracks = playbackState.availableTracks,
                        onTrackSelected = { group, index ->
                            onTrackSelected(group, index)
                            onDismiss()
                        },
                        onClearTrack = {
                            onClearTrackType(C.TRACK_TYPE_AUDIO)
                            onDismiss()
                        },
                        onBack = { currentPane = SettingsPane.MAIN }
                    )
                }
                SettingsPane.SUBTITLE -> {
                    TrackSelectionPane(
                        title = stringResource(R.string.player_subtitle),
                        type = C.TRACK_TYPE_TEXT,
                        availableTracks = playbackState.availableTracks,
                        onTrackSelected = { group, index ->
                            onTrackSelected(group, index)
                            onDismiss()
                        },
                        onClearTrack = {
                            onClearTrackType(C.TRACK_TYPE_TEXT)
                            onDismiss()
                        },
                        onBack = { currentPane = SettingsPane.MAIN }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPlayerSettingsSheet(
    playbackSpeed: Float,
    videoResizeMode: Int,
    availableTracks: Tracks?,
    streams: List<StreamInfo>,
    selectedStreamId: String?,
    onSpeedChange: (Float) -> Unit,
    onResizeModeChange: (Int) -> Unit,
    onStreamSelected: (StreamInfo) -> Unit,
    onTrackSelected: (Tracks.Group, Int) -> Unit,
    onClearTrackType: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        contentColor = TextPrimary
    ) {
        SettingsContent(
            playbackState = PlayerPlaybackState(
                playbackSpeed = playbackSpeed,
                videoResizeMode = videoResizeMode,
                availableTracks = availableTracks,
                streams = streams,
                selectedStreamId = selectedStreamId
            ),
            onSpeedChange = onSpeedChange,
            onResizeModeChange = onResizeModeChange,
            onStreamSelected = onStreamSelected,
            onTrackSelected = onTrackSelected,
            onClearTrackType = onClearTrackType,
            onDismiss = onDismiss
        )
    }
}

enum class SettingsPane { MAIN, SPEED, ASPECT_RATIO, QUALITY, AUDIO, SUBTITLE }

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(text = value, color = AnimeRed, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun PaneHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
        }
        Text(text = title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SelectableItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) AnimeRedContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isSelected) AnimeRed else TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = AnimeRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun TrackSelectionPane(
    title: String,
    type: Int,
    availableTracks: Tracks?,
    onTrackSelected: (Tracks.Group, Int) -> Unit,
    onClearTrack: () -> Unit,
    onBack: () -> Unit
) {
    PaneHeader(title, onBack = onBack)
    LazyColumn {
        item {
            SelectableItem(
                label = stringResource(R.string.player_track_none),
                isSelected = availableTracks?.let { it.groups.none { g -> g.type == type && g.isSelected } } ?: true,
                onClick = onClearTrack
            )
        }
        availableTracks?.groups?.filter { it.type == type }?.forEach { group ->
            items(group.length) { index ->
                val track = group.getTrackFormat(index)
                val label = track.label ?: track.language ?: "Track ${index + 1}"
                SelectableItem(
                    label = label,
                    isSelected = group.isTrackSelected(index),
                    onClick = { onTrackSelected(group, index) }
                )
            }
        }
    }
}

@Composable
fun getResizeModeLabel(mode: Int): String = when (mode) {
    3 -> stringResource(R.string.player_resize_fill)
    4 -> stringResource(R.string.player_resize_zoom)
    else -> stringResource(R.string.player_resize_fit)
}

@Composable
fun getSelectedTrackLabel(tracks: Tracks?, type: Int): String {
    val selectedGroup = tracks?.groups?.find { it.type == type && it.isSelected }
    if (selectedGroup == null) return stringResource(R.string.player_track_none)
    for (i in 0 until selectedGroup.length) {
        if (selectedGroup.isTrackSelected(i)) {
            val format = selectedGroup.getTrackFormat(i)
            return format.label ?: format.language ?: stringResource(R.string.player_track_default)
        }
    }
    return stringResource(R.string.player_track_none)
}

@Composable
fun ServerMenuContent(
    streams: List<StreamInfo>,
    selectedStreamId: String?,
    isLoading: Boolean,
    onStreamSelected: (StreamInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AnimeRed, modifier = Modifier.size(28.dp))
            }
        }
        streams.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.server_empty), color = TextMuted, fontSize = 13.sp)
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(streams) { stream ->
                    val isSelected = stream.id == selectedStreamId
                    Surface(
                        onClick = { onStreamSelected(stream) },
                        color = if (isSelected) AnimeRedContainer else SurfaceDark,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AnimeRed else CardBorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stream.name,
                                color = if (isSelected) AnimeRed else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, null, tint = AnimeRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
