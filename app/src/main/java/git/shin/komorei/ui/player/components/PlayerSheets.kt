package git.shin.komorei.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.Episode
import git.shin.komorei.model.WatchHistory
import git.shin.komorei.ui.components.EpisodeProgressBar
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesBottomSheet(
    anime: Anime,
    seasons: List<AnimeSeason>,
    currentEpisode: Episode,
    watchHistory: List<WatchHistory>, // New parameter
    selectedSeasonNumber: Int,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isGridView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isAscending by remember { mutableStateOf(true) }

    val activeSeason = remember(selectedSeasonNumber, seasons) {
        seasons.find { it.seasonNumber == selectedSeasonNumber } ?: seasons.firstOrNull()
        ?: AnimeSeason(anime.id, 1, "Phần 1", anime.episodes)
    }

    val filteredEpisodes = remember(activeSeason.episodes, searchQuery, isAscending) {
        val list = if (searchQuery.isBlank()) {
            activeSeason.episodes
        } else {
            activeSeason.episodes.filter { ep ->
                ep.episodeNumber.contains(searchQuery.trim()) ||
                        ep.title.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
        if (isAscending) list else list.reversed()
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("episodes_full_bottom_sheet")
        ) {
            /*Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardDark)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(6.dp))
                ) {
                    AsyncImage(
                        model = anime.posterUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.episodes_header, activeSeason.episodes.size),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${anime.title} • ${activeSeason.title}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

//                IconButton(
//                    onClick = onDismiss,
//                    modifier = Modifier
//                        .size(32.dp)
//                        .clip(CircleShape)
//                        .background(SurfaceDark)
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.Close,
//                        contentDescription = stringResource(R.string.cd_close_player),
//                        tint = TextSecondary,
//                        modifier = Modifier.size(16.dp)
//                    )
//                }
            }

            HorizontalDivider(color = CardBorderDark.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))
*/
            if (seasons.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(seasons) { season ->
                        val isSelected = season.seasonNumber == selectedSeasonNumber
                        Surface(
                            onClick = { onSeasonChange(season.seasonNumber) },
                            color = if (isSelected) AnimeRedContainer else SurfaceDark,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) AnimeRed else CardBorderDark
                            ),
                            modifier = Modifier.testTag("sheet_season_tab_${season.seasonNumber}")
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
                                    text = "${season.title} (${season.episodes.size})",
                                    color = if (isSelected) AnimeRed else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
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
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.episode_search_hint),
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Xóa",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
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
                            contentDescription = "Sắp xếp",
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
                            contentDescription = "Đổi kiểu hiển thị",
                            tint = if (isGridView) AnimeRed else TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.episode_display_count, filteredEpisodes.size),
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = if (isAscending) stringResource(R.string.sort_oldest) else stringResource(
                        R.string.sort_newest
                    ),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 56.dp),
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
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("sheet_grid_ep_${ep.episodeNumber}")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    if (isPlaying) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = stringResource(R.string.episode_playing_indicator),
                                                tint = AnimeRed,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = ep.episodeNumber,
                                                color = AnimeRed,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = ep.episodeNumber,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                
                                history?.let {
                                    EpisodeProgressBar(
                                        progress = it.progressFraction,
                                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
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
        modifier = Modifier
            .fillMaxWidth()
            .testTag("episode_list_item_${episode.episodeNumber}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x55000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isPlaying) AnimeRed else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(text = "24:00", color = Color.White, fontSize = 9.sp)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = episode.quality,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = " • ",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = stringResource(R.string.badge_sub),
                        color = AnimeRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AnimeRed)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.current_playing),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
