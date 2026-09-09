package git.shin.komorei.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.Episode
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.GoldRating
import git.shin.komorei.ui.theme.NeonCyan
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.SurfaceVariantDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailView(
    anime: Anime,
    currentEpisode: Episode,
    isBookmarked: Boolean,
    relatedAnimeList: List<Anime>,
    onEpisodeSelected: (Episode) -> Unit,
    onToggleBookmark: () -> Unit,
    onAnimeSelected: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var selectedServer by remember { mutableIntStateOf(0) }

    // Multi-Season / Parts support
    val effectiveSeasons = remember(anime.id, anime.seasons, anime.episodes) {
        if (anime.seasons.isNotEmpty()) {
            anime.seasons
        } else {
            listOf(AnimeSeason(1, "Phần 1", anime.episodes))
        }
    }

    var selectedSeasonNumber by remember(anime.id) {
        val initialSeason = effectiveSeasons.find { season ->
            season.episodes.any { it.id == currentEpisode.id }
        } ?: effectiveSeasons.firstOrNull()
        mutableIntStateOf(initialSeason?.seasonNumber ?: 1)
    }

    val currentSeason = remember(selectedSeasonNumber, effectiveSeasons) {
        effectiveSeasons.find { it.seasonNumber == selectedSeasonNumber } ?: effectiveSeasons.first()
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

                // Standard clean metadata text line: Năm • Số tập • Nguồn • Trạng thái
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${anime.releaseYear}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Text(text = " • ", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = "Tập ${anime.episodes.size}/${anime.episodeCount}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Text(text = " • ", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = anime.sourceName,
                        color = AnimeRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(text = " • ", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = anime.status,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Next Episode Air schedule if available (plain uncolored text without icon)
                if (!anime.nextEpisodeAirInfo.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = anime.nextEpisodeAirInfo,
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Rating & Genres
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = GoldRating,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = String.format("%.1f", anime.rating),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${anime.views} lượt xem • ${anime.studio}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• " + anime.genres.take(2).joinToString(", "),
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    label = if (isBookmarked) "Đã lưu" else "Lưu",
                    isActive = isBookmarked,
                    onClick = onToggleBookmark,
                    tag = "btn_bookmark"
                )

                DetailPillButton(
                    icon = Icons.Default.AutoAwesome,
                    label = "Tóm tắt",
                    isActive = false,
                    onClick = { showDescriptionSheet = true },
                    tag = "btn_summary"
                )

                DetailPillButton(
                    icon = Icons.Default.Flag,
                    label = "Báo cáo",
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
                    text = "Máy chủ phát",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServerOptionChip(
                        name = "Server 1 (FHD)",
                        isSelected = selectedServer == 0,
                        onClick = { selectedServer = 0 }
                    )
                    ServerOptionChip(
                        name = "Storage VIP",
                        isSelected = selectedServer == 1,
                        onClick = { selectedServer = 1 }
                    )
                    ServerOptionChip(
                        name = "Dự phòng",
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
                            text = "Tập phim",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${currentSeason.episodes.size} tập)",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Xem tất cả",
                            color = AnimeRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Xem tất cả tập",
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
                            if (isSelected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Đang phát",
                                        tint = AnimeRed,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format("%02d", ep.episodeNumber),
                                        color = AnimeRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = String.format("%02d", ep.episodeNumber),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
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

        // 5. BÌNH LUẬN PREVIEW
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorderDark, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Bình luận",
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
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Xem bình luận",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
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

        // 6. ĐỀ XUẤT CHO BẠN (3 ITEMS PER ROW RECTANGULAR POSTER GRID - 0.7 ASPECT RATIO)
        item {
            val relatedList = remember(anime.id, relatedAnimeList) {
                relatedAnimeList.filter { it.id != anime.id }
            }

            if (relatedList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Đề xuất cho bạn",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // 3-Column Grid with proper vertical poster ratio (0.7f ~ 2:3)
                val chunkedTriplets = remember(relatedList) {
                    relatedList.chunked(3)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    chunkedTriplets.forEach { rowTriplet ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowTriplet.forEach { itemAnime ->
                                Box(modifier = Modifier.weight(1f)) {
                                    RelatedAnimePosterCard(
                                        anime = itemAnime,
                                        onClick = { onAnimeSelected(itemAnime) }
                                    )
                                }
                            }
                            // Fill remaining space if last row has fewer than 3 items
                            val dummySlots = 3 - rowTriplet.size
                            repeat(dummySlots) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // 7. FULLSCREEN-CAPABLE EPISODES & SEASONS BOTTOM SHEET
    if (showEpisodesSheet) {
        EpisodesBottomSheet(
            anime = anime,
            seasons = effectiveSeasons,
            currentEpisode = currentEpisode,
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
                            text = "Thông tin & Giới thiệu",
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
                                text = "Năm: ${anime.releaseYear} • Studio: ${anime.studio}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Lượt xem: ${anime.views}",
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
                        text = "Tóm tắt cốt truyện",
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

                    MetadataDetailRow("Nguồn phát:", anime.sourceName)
                    MetadataDetailRow("Tình trạng:", anime.status)
                    MetadataDetailRow("Số tập hiện tại:", "${anime.episodes.size} / ${anime.episodeCount}")
                    MetadataDetailRow("Số phần (Seasons):", "${effectiveSeasons.size} phần")
                    MetadataDetailRow("Năm phát hành:", "${anime.releaseYear}")
                    MetadataDetailRow("Thể loại:", anime.genres.joinToString(", "))
                }
            }
        }
    }
}

/**
 * Fullscreen-capable interactive Episodes & Seasons Bottom Sheet
 * Polished, high-contrast, modern anime streaming UX.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesBottomSheet(
    anime: Anime,
    seasons: List<AnimeSeason>,
    currentEpisode: Episode,
    selectedSeasonNumber: Int,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    onDismiss: () -> Unit
) {
    // Allows bottom sheet to expand to full screen or collapse to half screen
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isGridView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isAscending by remember { mutableStateOf(true) }

    val activeSeason = remember(selectedSeasonNumber, seasons) {
        seasons.find { it.seasonNumber == selectedSeasonNumber } ?: seasons.firstOrNull() ?: AnimeSeason(1, "Phần 1", anime.episodes)
    }

    val filteredEpisodes = remember(activeSeason.episodes, searchQuery, isAscending) {
        val list = if (searchQuery.isBlank()) {
            activeSeason.episodes
        } else {
            activeSeason.episodes.filter { ep ->
                ep.episodeNumber.toString().contains(searchQuery.trim()) ||
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
            // Header Row: Anime Poster thumbnail, Title & Dismiss
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini vertical poster
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
                        text = "Danh sách tập phim",
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

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = CardBorderDark.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(10.dp))

            // Season / Part Tabs Row
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

            // Search Bar & View Mode / Sort Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input Field
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
                                        text = "Tìm theo số tập...",
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

                // Sort toggle (Ascending / Descending)
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

                // View Mode Toggle (Grid vs List)
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

            // Total count subtitle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hiển thị ${filteredEpisodes.size} tập",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = if (isAscending) "Cũ nhất trước" else "Mới nhất trước",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            // Episodes Content (Grid Mode or List Mode)
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

                        Surface(
                            onClick = { onEpisodeSelected(ep) },
                            color = if (isPlaying) AnimeRedContainer else SurfaceDark,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPlaying) AnimeRed else CardBorderDark
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("sheet_grid_ep_${ep.episodeNumber}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (isPlaying) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Đang phát",
                                            tint = AnimeRed,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = String.format("%02d", ep.episodeNumber),
                                            color = AnimeRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Text(
                                        text = String.format("%02d", ep.episodeNumber),
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // List Mode: Detailed Row Cards (Thumbnail + Episode Title + Duration + Quality)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredEpisodes) { ep ->
                        val isPlaying = ep.id == currentEpisode.id

                        EpisodeListItemCard(
                            episode = ep,
                            posterUrl = anime.posterUrl,
                            isPlaying = isPlaying,
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
            // Thumbnail Preview with Play Icon
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

                // Duration badge on bottom right
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
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Episode Info
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
                        text = "Vietsub",
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
                        text = "Đang phát",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ServerOptionChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) AnimeRedContainer else CardDark)
            .border(
                1.dp,
                if (isSelected) AnimeRed else CardBorderDark,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = name,
            color = if (isSelected) AnimeRed else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun CompactBadge(
    text: String,
    backgroundColor: Color = CardDark,
    textColor: Color = TextSecondary,
    isBordered: Boolean = false,
    isBold: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .then(
                if (isBordered) Modifier.border(1.dp, CardBorderDark, RoundedCornerShape(4.dp))
                else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun MetadataDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DetailPillButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String
) {
    Surface(
        onClick = onClick,
        color = if (isActive) AnimeRedContainer else CardDark,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) AnimeRed else CardBorderDark
        ),
        modifier = modifier
            .height(36.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) AnimeRed else TextPrimary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isActive) AnimeRed else TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Related Anime Portrait Card with 0.7 aspect ratio (2:3 standard anime poster)
 * Eliminates distortion and square stretching.
 */
@Composable
fun RelatedAnimePosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("related_anime_card_${anime.id}")
    ) {
        // 2:3 vertical poster box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(CardDark)
                .border(1.dp, CardBorderDark, RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model = anime.posterUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient shadow on bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xAA0A0D14))
                        )
                    )
            )

            // Top right compact episode badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AnimeRed.copy(alpha = 0.9f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Tập ${anime.episodes.size}/${anime.episodeCount}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom left rating
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = GoldRating,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = String.format("%.1f", anime.rating),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Title
        Text(
            text = anime.title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Metadata
        Text(
            text = "${anime.releaseYear} • ${anime.sourceName}",
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
