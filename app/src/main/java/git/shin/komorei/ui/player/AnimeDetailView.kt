package git.shin.komorei.ui.player

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.Episode
import git.shin.komorei.model.SelectedFilter
import git.shin.komorei.model.WatchHistory
import git.shin.komorei.ui.components.AnimeSection
import git.shin.komorei.ui.components.EpisodeProgressBar
import git.shin.komorei.ui.theme.Accent
import git.shin.komorei.ui.components.Badge
import git.shin.komorei.ui.components.DetailPillButton
import git.shin.komorei.ui.components.MetadataDetailRow
import git.shin.komorei.ui.components.ServerOptionChip
import git.shin.komorei.ui.player.components.EpisodesBottomSheet
import git.shin.komorei.ui.theme.Accent
import git.shin.komorei.ui.theme.AnimeGreen
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.GoldRating
import git.shin.komorei.ui.theme.NeonCyan
import git.shin.komorei.ui.theme.NoPaddingTextStyle
import git.shin.komorei.ui.theme.SmallTextStyle
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextGrey
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.utils.formatNumber

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailView(
    anime: Anime,
    currentEpisode: Episode,
    isBookmarked: Boolean,
    watchHistory: List<WatchHistory>, // New parameter
    relatedAnimeList: List<Anime>,
    onEpisodeSelected: (Episode) -> Unit,
    onToggleBookmark: () -> Unit,
    onAnimeSelected: (Anime) -> Unit,
    onNavigateToCategory: (List<SelectedFilter>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var selectedServer by remember { mutableIntStateOf(0) }

    // Multi-Season / Parts support
    val effectiveSeasons = remember(anime.id, anime.seasons, anime.episodes) {
        if (anime.seasons.isNotEmpty()) {
            anime.seasons
        } else {
            listOf(AnimeSeason(anime.id, 1, "Full Season", anime.episodes))
        }
    }

    var selectedSeasonNumber by remember(anime.id) {
        val initialSeason = effectiveSeasons.find { season ->
            season.episodes.any { it.id == currentEpisode.id }
        } ?: effectiveSeasons.firstOrNull()
        mutableIntStateOf(initialSeason?.seasonNumber ?: 1)
    }

    val currentSeason = remember(selectedSeasonNumber, effectiveSeasons) {
        effectiveSeasons.find { it.seasonNumber == selectedSeasonNumber }
            ?: effectiveSeasons.first()
    }

    val descSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundDark)
            .testTag("anime_detail_view"),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // 1. ANIME TITLE & METADATA BLOCK (Clickable to open YouTube-style Description Bottom Sheet)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDescriptionSheet = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("anime_header_info_block")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = anime.title,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Xem giới thiệu",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // View + next time play
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.views_count, formatNumber(anime.views)),
                        color = TextGrey,
                        fontSize = 14.sp,
                        style = NoPaddingTextStyle
                    )

                    anime.nextEpisodeAirInfo?.let { text ->
                        Text(
                            text = " • ",
                            color = TextGrey,
                            fontSize = 14.sp,
                            style = NoPaddingTextStyle
                        )

                        Text(
                            text = text,
                            color = Accent,
                            fontSize = 14.sp,
                            style = NoPaddingTextStyle
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Author and Studio Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (anime.authors.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.author_label) + " ",
                                color = TextGrey,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle
                            )

                            Text(
                                text = anime.authors.first().name,
                                color = if (anime.authors.first().filters.isNotEmpty()) AnimeGreen else TextPrimary,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                                modifier = Modifier.clickable(enabled = anime.authors.first().filters.isNotEmpty()) {
                                    onNavigateToCategory(anime.authors.first().filters)
                                }
                            )

                            Text(
                                text = " | ",
                                color = TextGrey,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle
                            )
                        }

                        Text(
                            text = stringResource(
                                R.string.studio_prefix,
                                anime.studio?.name ?: stringResource(R.string.unknown)
                            ),
                            color = if (anime.studio != null && anime.studio.filters.isNotEmpty()) AnimeGreen else TextGrey,
                            fontSize = 14.sp,
                            style = NoPaddingTextStyle,
                            modifier = Modifier.clickable(enabled = anime.studio != null && anime.studio.filters.isNotEmpty()) {
                                anime.studio?.let {
                                    onNavigateToCategory(it.filters)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Badges row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!anime.qualityTag.isNullOrEmpty()) {
                            Text(
                                text = anime.qualityTag,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                style = NoPaddingTextStyle,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .background(
                                        Color(0xFF00C853).copy(alpha = .85f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (anime.releaseYear != null) {
                            Badge(
                                text = anime.releaseYear.name,
                                textStyle = NoPaddingTextStyle,
                                modifier = Modifier.clickable(enabled = anime.releaseYear.filters.isNotEmpty()) {
                                    onNavigateToCategory(anime.releaseYear.filters)
                                }
                            )
                        }
                        if (!anime.currentEpisode.isNullOrEmpty()) {
                            Badge(
                                text = stringResource(
                                    R.string.updated_to_episode,
                                    anime.currentEpisode
                                ),
                                textStyle = NoPaddingTextStyle
                            )
                        }
                        if (anime.countries.isNotEmpty()) {
                            Text(
                                text = anime.countries.first().name,
                                color = if (anime.countries.first().filters.isNotEmpty()) AnimeGreen else TextPrimary,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                                modifier = Modifier.clickable(enabled = anime.countries.first().filters.isNotEmpty()) {
                                    onNavigateToCategory(anime.countries.first().filters)
                                }
                            )
                        }
                    }

                    // Rating info (Stars on new line)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = anime.rating.toString(),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            style = NoPaddingTextStyle
                        )
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = GoldRating,
                            modifier = Modifier.size(14.dp)
                        )

                        anime.ratingCount?.let {
                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(
                                    R.string.rating_count,
                                    formatNumber(it)
                                ),
                                color = TextGrey,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle
                            )
                        }
                        anime.seasonOf?.let {
                            Text(
                                text = " | ",
                                color = TextGrey,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle
                            )

                            Text(
                                text = it.name,
                                color = if (it.filters.isNotEmpty()) AnimeGreen else TextPrimary,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                                modifier = Modifier.clickable(enabled = it.filters.isNotEmpty()) {
                                    onNavigateToCategory(it.filters)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tags/Genres
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        anime.genres.forEach { genre ->
                            Text(
                                text = "#${genre.name}",
                                color = if (genre.filters.isNotEmpty()) AnimeGreen else TextSecondary,
                                fontSize = 14.sp,
                                style = SmallTextStyle,
                                modifier = Modifier
                                    .padding(vertical = 1.dp)
                                    .clickable(enabled = genre.filters.isNotEmpty()) {
                                        onNavigateToCategory(genre.filters)
                                    }
                            )
                        }
                    }

                }
            }
        }

        // 2. ACTION BUTTONS ROW: [=+ Lưu] [✨ Tóm tắt] [🚩 Báo cáo] (Wrap content width according to text)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailPillButton(
                    icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkAdd,
                    label = if (isBookmarked) stringResource(R.string.following_anime) else stringResource(
                        R.string.follow_anime
                    ),
                    isActive = isBookmarked,
                    onClick = onToggleBookmark,
                    tag = "btn_bookmark"
                )

                DetailPillButton(
                    icon = Icons.Default.AutoAwesome,
                    label = stringResource(R.string.description_title),
                    isActive = false,
                    onClick = { showDescriptionSheet = true },
                    tag = "btn_summary"
                )

                DetailPillButton(
                    icon = Icons.Default.Flag,
                    label = stringResource(R.string.report_anime),
                    isActive = false,
                    onClick = { },
                    tag = "btn_report"
                )
            }
        }

        // 3. MÁY CHỦ PHÁT (STREAMING SERVERS)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.streaming_server_header),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServerOptionChip(
                        name = stringResource(R.string.server_name_fhd),
                        isSelected = selectedServer == 0,
                        onClick = { selectedServer = 0 }
                    )
                    ServerOptionChip(
                        name = stringResource(R.string.server_name_vip),
                        isSelected = selectedServer == 1,
                        onClick = { selectedServer = 1 }
                    )
                    ServerOptionChip(
                        name = stringResource(R.string.server_name_backup),
                        isSelected = selectedServer == 2,
                        onClick = { selectedServer = 2 }
                    )
                }
            }
        }

        // 4. TẬP PHIM (EPISODE SELECTOR & SEASONS/PARTS BELOW EPISODES)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp)
            ) {
                // Header: Clickable to open Fullscreen Drag Bottom Sheet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEpisodesSheet = true }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("btn_open_episodes_sheet"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.episodes_header,
                                currentSeason.episodes.size
                            ),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.section_see_all),
                            color = AnimeRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = stringResource(R.string.section_see_all),
                            tint = AnimeRed,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 1. Episode Number Chips (01, 02, 03...) - Displayed FIRST
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(currentSeason.episodes) { ep ->
                        val isSelected = ep.id == currentEpisode.id

                        val history = watchHistory.find { it.episodeId == ep.id }
                        
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AnimeRedContainer else CardDark)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) AnimeRed else CardBorderDark,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onEpisodeSelected(ep) }
                                .testTag("episode_chip_${ep.episodeNumber}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isSelected) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Playing",
                                            tint = AnimeRed,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = ep.episodeNumber,
                                            color = AnimeRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
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

                // 2. Seasons / Parts Selector - Placed BELOW the episode list on screen
                if (effectiveSeasons.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        items(effectiveSeasons) { season ->
                            val isSeasonSelected = season.seasonNumber == selectedSeasonNumber
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSeasonSelected) AnimeRedContainer else CardDark)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSeasonSelected) AnimeRed else CardBorderDark,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedSeasonNumber = season.seasonNumber }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("season_tab_${season.seasonNumber}")
                            ) {
                                Text(
                                    text = "${season.title} (${season.episodes.size} tập)",
                                    color = if (isSeasonSelected) AnimeRed else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSeasonSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. BÌNH LUẬN PREVIEW (Clickable to open Comments Bottom Sheet)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorderDark, RoundedCornerShape(10.dp))
                    .clickable { showCommentsSheet = true }
                    .padding(12.dp)
                    .testTag("comments_preview_section")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.comments_title),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "821",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Xem tất cả bình luận",
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(AnimeRedContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "K",
                            color = AnimeRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bộ này chuyển thể từ manga nét vẽ đỉnh chóp, nhạc nền xuất sắc!",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 6. ĐỀ XUẤT CHO BẠN
        item {
            if (relatedAnimeList.isNotEmpty()) {
                AnimeSection(
                    title = stringResource(R.string.related_anime_header),
                    animeList = relatedAnimeList,
                    onAnimeClick = onAnimeSelected
                )
            }
        }
    }

    // 7. FULLSCREEN-CAPABLE EPISODES & SEASONS BOTTOM SHEET
    if (showEpisodesSheet) {
        EpisodesBottomSheet(
            anime = anime,
            seasons = effectiveSeasons,
            currentEpisode = currentEpisode,
            watchHistory = watchHistory, // Pass it here
            selectedSeasonNumber = selectedSeasonNumber,
            onSeasonChange = { selectedSeasonNumber = it },
            onEpisodeSelected = { ep ->
                onEpisodeSelected(ep)
                showEpisodesSheet = false
            },
            onDismiss = { showEpisodesSheet = false }
        )
    }

    // 8. YOUTUBE-STYLE DESCRIPTION MODAL BOTTOM SHEET (WITH VERTICAL POSTER)
    if (showDescriptionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDescriptionSheet = false },
            sheetState = descSheetState,
            containerColor = SurfaceDark,
            contentColor = TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CardBorderDark)
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 32.dp)
            ) {
                item {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.info_summary_title),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showDescriptionSheet = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Anime Header with Vertical Poster & Core details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Vertical Poster (0.7f ~ 2:3 ratio)
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardDark)
                                .border(1.dp, CardBorderDark, RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = anime.posterUrl,
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Rating Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = GoldRating,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format("%.1f", anime.rating),
                                        color = TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title, original name, studio & views
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = anime.title,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = anime.originalTitle,
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "${stringResource(R.string.metadata_year)} ${anime.releaseYear} • Studio: ${anime.studio}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = stringResource(R.string.views_format, anime.views),
                                color = AnimeRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (!anime.nextEpisodeAirInfo.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "⏰ ${anime.nextEpisodeAirInfo}",
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorderDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.plot_summary_title),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = anime.description,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorderDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    MetadataDetailRow(stringResource(R.string.metadata_source), anime.sourceName)
                    MetadataDetailRow(
                        stringResource(R.string.metadata_status),
                        when (anime.status) {
                            AnimeStatus.ONGOING -> stringResource(R.string.status_ongoing)
                            AnimeStatus.COMPLETED -> stringResource(R.string.status_completed)
                            else -> stringResource(R.string.unknown)
                        }
                    )
                    MetadataDetailRow(
                        stringResource(R.string.metadata_episodes),
                        anime.currentEpisode ?: "??/??"
                    )
                    MetadataDetailRow(
                        stringResource(R.string.metadata_seasons),
                        stringResource(R.string.metadata_seasons_count, effectiveSeasons.size)
                    )
                    MetadataDetailRow(
                        stringResource(R.string.metadata_year),
                        "${anime.releaseYear}"
                    )
                    MetadataDetailRow(
                        stringResource(R.string.metadata_genres),
                        anime.genres.joinToString(", ")
                    )
                }
            }
        }
    }
}
