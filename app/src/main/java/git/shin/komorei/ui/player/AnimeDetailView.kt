package git.shin.komorei.ui.player

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeSeason
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.Episode
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.ui.components.AnimeCard
import git.shin.komorei.ui.components.Badge
import git.shin.komorei.ui.components.DetailPillButton
import git.shin.komorei.ui.components.EpisodeProgressBar
import git.shin.komorei.ui.components.GRID_GUTTER
import git.shin.komorei.ui.components.GRID_ROW_SPACING
import git.shin.komorei.ui.components.MetadataDetailRow
import git.shin.komorei.ui.components.PAGE_PADDING_HORIZONTAL
import git.shin.komorei.ui.components.SectionHeader
import git.shin.komorei.ui.components.ServerOptionChip
import git.shin.komorei.ui.components.animeGridColumnCount
import git.shin.komorei.ui.components.gridCellWidth
import git.shin.komorei.ui.components.rememberSystemNavigationBarBottom
import git.shin.komorei.ui.components.shimmerEffect
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
import git.shin.komorei.ui.tv.tvFocus
import git.shin.komorei.ui.utils.animateScrollToItemCentered
import git.shin.komorei.ui.utils.formatNumber
import kotlinx.coroutines.flow.first

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailView(
    anime: Anime,
    fullAnime: Anime?,
    currentEpisode: Episode,
    relatedAnimeList: List<Anime>,
    streams: List<StreamInfo>,
    selectedStreamId: String?,
    isLoadingStreams: Boolean,
    streamError: String?,
    onEpisodeSelected: (Episode) -> Unit,
    onStreamSelected: (StreamInfo) -> Unit,
    onRetryStreams: () -> Unit,
    onAnimeSelected: (Anime) -> Unit,
    onNavigateToCategory: (List<FilterValue>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()

    // The player upgrades the anime itself — `loadStreams` asks for details AND
    // chapters in one call — and publishes the result on `playbackState`. Wait for
    // that instead of racing it: fetching the same anime from here as well meant
    // two identical round trips to the source for one screen. `isLoadingStreams`
    // is what says the player's load has settled — once it is false and there is
    // still no full anime, that load failed (or this screen was reached without
    // the player), and fetching here is the only way to get the data at all.
    LaunchedEffect(anime.id, fullAnime?.id, isLoadingStreams) {
        when {
            fullAnime != null -> viewModel.loadInitialData(anime, fullAnime)
            !isLoadingStreams -> viewModel.loadInitialData(anime)
        }
    }

    val displayAnime = uiState.masterAnime ?: anime
    val episodes = uiState.currentSeasonEpisodes
    val selectedSeason = uiState.selectedSeason

    var showDescriptionSheet by remember { mutableStateOf(false) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

    // Per-season scroll state for the detail-screen episode strip. A single shared
    // LazyListState gets re-scrolled to the active episode every time you return to a
    // season — wiping the position you'd left it at. Each season keeps its own row
    // state, hoisted ABOVE the LazyColumn because its item blocks leave composition
    // when scrolled out of view (which would reset a remember() living inside).
    val detailEpisodeRowStates = remember { HashMap<String, LazyListState>() }

    // Row state/key for the currently-viewed season's episode strip, shared by the LazyRow
    // below and the hoisted follow-active auto-scroll. Each season keeps its own row state.
    val detailRowKey =
        if (uiState.selectedVirtualSeasonId != null) {
            "row:${uiState.selectedVirtualSeasonId}"
        } else {
            "row:${uiState.selectedSeason?.animeId ?: displayAnime.id}"
        }
    val detailRowState = detailEpisodeRowStates.getOrPut(detailRowKey) { LazyListState() }

    // Follow-the-playing-episode auto-scroll for the episode strip. Hoisted ABOVE the
    // LazyColumn: its item blocks leave composition when scrolled out of view, and a
    // LaunchedEffect living inside would restart on every scroll-by and re-center the strip.
    // Keyed on (season rowKey, playing episode id) it re-fires on every SEASON entry and on
    // every PLAYING-EPISODE change — so returning to a season centers the strip on the active
    // chapter, and picking a new episode (from here or the player sheet) scrolls the strip to
    // follow — while a manual scroll within a season is left alone. Never key on the
    // `episodes` list reference: a fresh instance per VM emission would restart the effect
    // every recomposition and cancel the in-flight layout wait. Everything re-reads snapshot
    // state inside the flow, so it also survives the loading skeleton (5 placeholder chips
    // that fill this row), empty and error phases, and the strip being temporarily off-screen.
    LaunchedEffect(detailRowKey, currentEpisode.id) {
        snapshotFlow {
            val eps = uiState.currentSeasonEpisodes
            Triple(
                eps.indexOfFirst { it.id == currentEpisode.id },
                detailRowState.layoutInfo.totalItemsCount,
                uiState.isLoadingEpisodes,
            )
        }.first { (index, count, loading) -> index >= 0 && !loading && count > index }
        val target = uiState.currentSeasonEpisodes.indexOfFirst { it.id == currentEpisode.id }
        if (target >= 0 && detailRowState.layoutInfo.totalItemsCount > target) {
            detailRowState.animateScrollToItemCentered(target)
        }
    }

    val realSeasons =
        displayAnime.seasons.ifEmpty {
            listOf(AnimeSeason(displayAnime.id, stringResource(R.string.season_fallback_full)))
        }
    // A huge real season (e.g. Conan, 1000+ episodes) is expanded in the picker into
    // 50-episode "virtual seasons" (same animeId, distinct id), so its single chip is
    // replaced by the chunk chips while the other real seasons stay as they are.
    val effectiveSeasons =
        if (uiState.virtualSeasons.isEmpty()) {
            realSeasons
        } else {
            val parentId = uiState.selectedSeason?.animeId ?: displayAnime.id
            realSeasons.flatMap { season ->
                if (season.animeId == parentId) uiState.virtualSeasons else listOf(season)
            }
        }

    val descSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LazyColumn(
        modifier =
            modifier
                .fillMaxWidth()
                .background(BackgroundDark)
                .testTag("anime_detail_view"),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            // The anime reaching this screen is the Lite card from a listing:
            // title and poster, nothing else. Rendering it while the upgrade is
            // in flight shows a real-looking header with an empty studio, no
            // genres, no seasons and a zeroed rating — it reads as broken data
            // rather than as loading. Skeleton until `masterAnime` is the real
            // record, then swap; the placeholder is shaped like the header so the
            // swap does not move anything.
            if (uiState.isLoadingMetadata) {
                AnimeDetailHeaderSkeleton()
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { showDescriptionSheet = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = displayAnime.title,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_introduction),
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Một Text duy nhất cho cả đoạn "lượt xem • Tập tiếp theo..." —
                        // nếu để Text riêng trong Row, khi quá dài nó wrap và dòng thứ 2
                        // thụt theo chỗ bắt đầu của chữ "Tập tiếp theo". Gộp chung một
                        // paragraph (AnnotatedString giữ màu) để dòng xuống bắt đầu từ
                        // mép trái, thẳng với số lượt xem.
                        displayAnime.nextEpisodeAirInfo?.let { info ->
                            Text(
                                text =
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = TextGrey)) {
                                            append(stringResource(R.string.views_count, formatNumber(displayAnime.views)))
                                            append(" • ")
                                        }
                                        withStyle(SpanStyle(color = Accent)) {
                                            append(info)
                                        }
                                    },
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                            )
                        } ?: run {
                            Text(
                                text =
                                    stringResource(
                                        R.string.views_count,
                                        formatNumber(displayAnime.views),
                                    ),
                                color = TextGrey,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (displayAnime.authors.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.author_label) + " " + displayAnime.authors.first().name,
                                color =
                                    if (displayAnime.authors
                                            .first()
                                            .filters
                                            .isNotEmpty()
                                    ) {
                                        AnimeGreen
                                    } else {
                                        TextSecondary
                                    },
                                fontSize = 14.sp,
                                modifier =
                                    Modifier
                                        // TV focus highlight (no-op on phones).
                                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.08f)
                                        .clickable(
                                            enabled =
                                                displayAnime.authors
                                                    .first()
                                                    .filters
                                                    .isNotEmpty(),
                                        ) {
                                            onNavigateToCategory(displayAnime.authors.first().filters)
                                        },
                            )
                            Text(text = " | ", color = TextGrey, fontSize = 14.sp)
                        }

                        Text(
                            text =
                                stringResource(
                                    R.string.studio_prefix,
                                    displayAnime.studio?.name ?: stringResource(R.string.unknown),
                                ),
                            color = if (displayAnime.studio != null && displayAnime.studio.filters.isNotEmpty()) AnimeGreen else TextSecondary,
                            fontSize = 14.sp,
                            modifier =
                                Modifier
                                    // TV focus highlight (no-op on phones).
                                    .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.08f)
                                    .clickable(enabled = displayAnime.studio != null && displayAnime.studio.filters.isNotEmpty()) {
                                        displayAnime.studio?.let { onNavigateToCategory(it.filters) }
                                    },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        displayAnime.qualityTag?.let {
                            Text(
                                text = it,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                style = NoPaddingTextStyle,
                                modifier =
                                    Modifier
                                        .background(
                                            Color(0xFF00C853).copy(alpha = .85f),
                                            RoundedCornerShape(4.dp),
                                        ).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        displayAnime.releaseYear?.let {
                            Badge(
                                text = it.name,
                                textStyle = NoPaddingTextStyle,
                                modifier =
                                    Modifier
                                        // TV focus highlight (no-op on phones).
                                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.08f)
                                        .clickable { onNavigateToCategory(it.filters) },
                            )
                        }
                        if (!displayAnime.currentEpisode.isNullOrEmpty()) {
                            Badge(
                                text =
                                    stringResource(
                                        R.string.updated_to_episode,
                                        displayAnime.currentEpisode,
                                    ),
                                textStyle = NoPaddingTextStyle,
                            )
                        }
                        displayAnime.countries.forEach { country ->
                            Text(
                                text = country.name,
                                color = if (country.filters.isNotEmpty()) AnimeGreen else TextSecondary,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                                modifier =
                                    Modifier
                                        // TV focus highlight (no-op on phones).
                                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.08f)
                                        .clickable(enabled = country.filters.isNotEmpty()) {
                                            onNavigateToCategory(country.filters)
                                        },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Rating info (Stars + Rating Count + SeasonOf Link)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = String.format("%.1f", displayAnime.rating ?: 0f),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            style = NoPaddingTextStyle,
                        )
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = GoldRating,
                            modifier = Modifier.size(14.dp),
                        )

                        displayAnime.ratingCount?.let {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.rating_count, formatNumber(it)),
                                color = TextGrey,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                            )
                        }

                        displayAnime.seasonOf?.let {
                            Text(
                                text = " | ",
                                color = TextGrey,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                            )

                            Text(
                                text = it.name,
                                color = if (it.filters.isNotEmpty()) AnimeGreen else TextPrimary,
                                fontSize = 14.sp,
                                style = NoPaddingTextStyle,
                                modifier =
                                    Modifier
                                        // TV focus highlight (no-op on phones).
                                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.08f)
                                        .clickable(enabled = it.filters.isNotEmpty()) {
                                            onNavigateToCategory(it.filters)
                                        },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        displayAnime.genres.forEach { genre ->
                            Text(
                                text = "#${genre.name}",
                                color = if (genre.filters.isNotEmpty()) AnimeGreen else TextSecondary,
                                fontSize = 14.sp,
                                style = SmallTextStyle,
                                modifier =
                                    Modifier
                                        // TV focus highlight (no-op on phones).
                                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.08f)
                                        .clickable(enabled = genre.filters.isNotEmpty()) {
                                            onNavigateToCategory(genre.filters)
                                        },
                            )
                        }
                    }
                }
            }
        }

        item {
            LazyRow(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    DetailPillButton(
                        icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkAdd,
                        label =
                            if (isBookmarked) {
                                stringResource(R.string.following_anime)
                            } else {
                                stringResource(
                                    R.string.follow_anime,
                                )
                            },
                        isActive = isBookmarked,
                        onClick = { viewModel.toggleBookmark() },
                        tag = "bookmark",
                    )
                }
                item {
                    DetailPillButton(
                        icon = Icons.Default.AutoAwesome,
                        label = stringResource(R.string.description_title),
                        isActive = false,
                        onClick = { showDescriptionSheet = true },
                        tag = "summary",
                    )
                }
                item {
                    DetailPillButton(
                        icon = Icons.Default.Flag,
                        label = stringResource(R.string.report_anime),
                        isActive = false,
                        onClick = { },
                        tag = "report",
                    )
                }
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.streaming_server_header),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    // Server list is fetched dynamically via getStreamList(fullAnime, episode).
                    // Skeleton uses the SAME padding + 12sp label as ServerOptionChip so the
                    // swap doesn't jump vertically (a fixed 30dp box is ~8dp shorter than the
                    // real chip, whose line box is 24sp + 7dp x2 padding).
                    isLoadingStreams ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(serverSkeletonCount(displayAnime.sourceId)) {
                                Box(
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CardDark.copy(alpha = 0.5f))
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.placeholder_loading),
                                        color = Color.Transparent,
                                        fontSize = 12.sp,
                                        // Invisible measurement text — keep TalkBack quiet about it.
                                        modifier = Modifier.clearAndSetSemantics { },
                                    )
                                }
                            }
                        }
                    streamError != null ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = streamError,
                                color = TextGrey,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.action_retry),
                                color = AnimeRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onRetryStreams() }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    else ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            streams.forEach { stream ->
                                ServerOptionChip(
                                    name = stream.name,
                                    isSelected = stream.id == selectedStreamId,
                                    onClick = { onStreamSelected(stream) },
                                )
                            }
                        }
                }
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // TV focus highlight (no-op on phones) — the "episodes
                            // header" acts as a see-all target on the detail page.
                            .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.02f)
                            .clickable { showEpisodesSheet = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.isLoadingMetadata || uiState.isLoadingEpisodes) {
                        // The count comes off the record, and `episodeCount` is 0
                        // on the Lite card the listing handed us — so printing it
                        // here puts a real "Danh sách tập (0 tập)" on screen
                        // while the list is still arriving. A placeholder bar
                        // says the same thing without the lie.
                        //
                        // Both flags, not just the episodes one: while the screen
                        // is still waiting on the player's upgrade the load has
                        // not even been kicked off yet, so `isLoadingEpisodes` is
                        // still at its default `false` and the record on hand is
                        // the Lite one.
                        Box(
                            modifier =
                                Modifier
                                    .width(150.dp)
                                    .height(15.dp)
                                    .shimmerEffect(RoundedCornerShape(4.dp)),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.episodes_header, displayAnime.episodeCount),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.section_see_all),
                            color = AnimeRed,
                            fontSize = 12.sp,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = AnimeRed,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }

                LazyRow(
                    state = detailRowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isLoadingEpisodes) {
                        items(5) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 46.dp, height = 34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardDark.copy(alpha = 0.5f)),
                            )
                        }
                    } else if (uiState.episodeError != null) {
                        // Season load failed — keep the row visible with the error + retry.
                        item {
                            Row(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardDark.copy(alpha = 0.3f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = uiState.episodeError ?: "",
                                    color = TextGrey,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 220.dp),
                                )
                                Text(
                                    text = stringResource(R.string.action_retry),
                                    color = AnimeRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { viewModel.retryEpisodes() }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    } else if (episodes.isEmpty()) {
                        // Season has no episodes yet — keep the row visible instead of
                        // collapsing; span the full width and center the message.
                        item {
                            Box(
                                modifier =
                                    Modifier
                                        .fillParentMaxWidth()
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardDark.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.episodes_empty),
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(episodes) { ep ->
                            val isSelected = ep.id == currentEpisode.id
                            val history = watchHistory.find { it.episodeId == ep.id }

                            Box(
                                modifier =
                                    Modifier
                                        .size(width = 46.dp, height = 34.dp)
                                        // TV focus highlight (no-op on phones).
                                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.08f, borderWidth = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AnimeRedContainer else CardDark)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) AnimeRed else CardBorderDark,
                                            shape = RoundedCornerShape(8.dp),
                                        ).clickable { onEpisodeSelected(ep) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
//                                        if (isSelected) {
//                                            Icon(
//                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
//                                                contentDescription = null,
//                                                tint = AnimeRed,
//                                                modifier = Modifier.size(14.dp)
//                                            )
//                                        } else {
                                        Text(
                                            text = ep.episodeNumber,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                        )
//                                        }
                                    }
                                    history?.let {
                                        EpisodeProgressBar(
                                            progress = it.progressFraction,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (effectiveSeasons.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val seasonsRowState = rememberLazyListState()
                    val activeSeasonIndex =
                        effectiveSeasons.indexOfFirst { season ->
                            if (uiState.selectedVirtualSeasonId != null) {
                                season.id == uiState.selectedVirtualSeasonId
                            } else {
                                season.animeId == uiState.selectedSeason?.animeId
                            }
                        }
                    LaunchedEffect(
                        uiState.selectedSeason?.animeId,
                        uiState.selectedVirtualSeasonId,
                        effectiveSeasons.size,
                    ) {
                        seasonsRowState.animateScrollToItemCentered(activeSeasonIndex)
                    }
                    LazyRow(
                        state = seasonsRowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(effectiveSeasons) { season ->
                            val isSeasonSelected =
                                if (uiState.selectedVirtualSeasonId != null) {
                                    season.id == uiState.selectedVirtualSeasonId
                                } else {
                                    season.animeId == uiState.selectedSeason?.animeId
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSeasonSelected) AnimeRedContainer else CardDark)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSeasonSelected) AnimeRed else CardBorderDark,
                                            shape = RoundedCornerShape(6.dp),
                                        ).clickable { viewModel.selectSeason(season) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = season.title,
                                    color = if (isSeasonSelected) AnimeRed else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSeasonSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(10.dp))
                        .clickable { showCommentsSheet = true }
                        .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.comments_title),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "821",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = stringResource(R.string.cd_view_comments),
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(AnimeRedContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "K",
                            color = AnimeRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Great adaptation, the soundtrack is amazing!",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            if (relatedAnimeList.isNotEmpty()) {
                // Responsive column count (3 phone / 4 tablet / 5-6 TV),
                // rendered NON-lazily: a LazyVerticalGrid nested inside this
                // LazyColumn item would be measured with an infinite max-height
                // (items of LazyColumn get unbounded height), which crashes Compose.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = animeGridColumnCount()
                    val rows = relatedAnimeList.chunked(columns)
                    // Sized against the width the rows actually have, padding
                    // included — see gridCellWidth. Measuring against the
                    // container instead made the row ask for more than it had, and
                    // Compose squeezed the last card to fit.
                    val cellWidth = gridCellWidth(columns)
                    // One Column, so the header sits above the grid. As siblings
                    // of a Box these two overlapped, and the grid drew over its
                    // own heading.
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(title = stringResource(R.string.related_anime_header))
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = PAGE_PADDING_HORIZONTAL),
                        ) {
                            rows.forEachIndexed { index, rowAnimes ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                bottom =
                                                    if (index < rows.lastIndex) {
                                                        GRID_ROW_SPACING
                                                    } else {
                                                        0.dp
                                                    },
                                            ),
                                    horizontalArrangement = Arrangement.spacedBy(GRID_GUTTER),
                                ) {
                                    // Fixed cell width (not weight) so an incomplete last row keeps
                                    // the same sizing as full rows instead of stretching to 50%.
                                    rowAnimes.forEach { anime ->
                                        AnimeCard(
                                            anime = anime,
                                            onClick = { onAnimeSelected(anime) },
                                            cardWidth = cellWidth,
                                        )
                                    }
                                    // Keep an incomplete last row left-aligned.
                                    repeat(columns - rowAnimes.size) {
                                        Spacer(modifier = Modifier.width(cellWidth))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEpisodesSheet) {
        EpisodesBottomSheet(
            anime = displayAnime,
            seasons = effectiveSeasons,
            currentEpisode = currentEpisode,
            watchHistory = watchHistory,
            selectedSeasonId = uiState.selectedVirtualSeasonId ?: selectedSeason?.animeId ?: displayAnime.id,
            onSeasonChange = { id ->
                effectiveSeasons.find { it.id == id }?.let { viewModel.selectSeason(it) }
            },
            episodes = episodes,
            episodesError = uiState.episodeError,
            isLoading = uiState.isLoadingEpisodes,
            onRetryEpisodes = { viewModel.retryEpisodes() },
            onEpisodeSelected = { ep ->
                onEpisodeSelected(ep)
                showEpisodesSheet = false
            },
            onDismiss = { showEpisodesSheet = false },
        )
    }

    if (showDescriptionSheet) {
        val navBarBottom = rememberSystemNavigationBarBottom()
        ModalBottomSheet(
            onDismissRequest = { showDescriptionSheet = false },
            sheetState = descSheetState,
            containerColor = SurfaceDark,
            contentColor = TextPrimary,
            dragHandle = {
                Box(
                    modifier =
                        Modifier
                            .padding(top = 10.dp, bottom = 6.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CardBorderDark),
                )
            },
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = navBarBottom + 24.dp),
            ) {
                item {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.info_summary_title),
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = { showDescriptionSheet = false },
                            modifier =
                                Modifier
                                    // TV focus highlight (no-op on phones).
                                    .tvFocus(shape = CircleShape, scale = 1.15f)
                                    .size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Anime Header with Vertical Poster & Core details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Vertical Poster (0.7f ~ 2:3 ratio)
                        Box(
                            modifier =
                                Modifier
                                    .width(96.dp)
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardDark)
                                    .border(1.dp, CardBorderDark, RoundedCornerShape(8.dp)),
                        ) {
                            AsyncImage(
                                model = displayAnime.posterUrl,
                                contentDescription = displayAnime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Rating Badge
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xCC000000))
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = GoldRating,
                                        modifier = Modifier.size(10.dp),
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format("%.1f", displayAnime.rating ?: 0f),
                                        color = TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title, original name, studio & views
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayAnime.title,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = displayAnime.originalTitle,
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "${stringResource(R.string.metadata_year)} ${displayAnime.releaseYear?.name ?: ""} • Studio: ${displayAnime.studio?.name ?: ""}",
                                color = TextSecondary,
                                fontSize = 12.sp,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text =
                                    stringResource(
                                        R.string.views_format,
                                        formatNumber(displayAnime.views),
                                    ),
                                color = AnimeRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            displayAnime.nextEpisodeAirInfo?.let { text ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⏰ $text",
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp,
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
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayAnime.description,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorderDark)
                    Spacer(modifier = Modifier.height(12.dp))

                    MetadataDetailRow(stringResource(R.string.metadata_source), uiState.sourceName)
                    MetadataDetailRow(
                        stringResource(R.string.metadata_status),
                        when (displayAnime.status) {
                            AnimeStatus.ONGOING -> stringResource(R.string.status_ongoing)
                            AnimeStatus.COMPLETED -> stringResource(R.string.status_completed)
                            else -> stringResource(R.string.unknown)
                        },
                    )
                    MetadataDetailRow(
                        stringResource(R.string.metadata_episodes),
                        displayAnime.currentEpisode ?: stringResource(R.string.episode_range_unknown),
                    )
                    MetadataDetailRow(
                        stringResource(R.string.metadata_seasons),
                        stringResource(R.string.metadata_seasons_count, realSeasons.size),
                    )
                    displayAnime.seasonOf?.let {
                        MetadataDetailRow(stringResource(R.string.metadata_belongs_to_series), it.name)
                    }
                    MetadataDetailRow(
                        stringResource(R.string.metadata_year),
                        displayAnime.releaseYear?.name ?: stringResource(R.string.unknown),
                    )
                    MetadataDetailRow(
                        stringResource(R.string.metadata_country),
                        displayAnime.countries.joinToString(", ") { it.name },
                    )
                    MetadataDetailRow(
                        stringResource(R.string.metadata_genres),
                        displayAnime.genres.joinToString(", ") { it.name },
                    )
                }
            }
        }
    }
}

/**
 * Mirrors `AnimeRepository.getStreamList()` so the server-list loading skeleton shows the
 * same number of chips the source will actually return (avoids a layout shift on swap).
 */
private fun serverSkeletonCount(sourceId: String): Int =
    when (sourceId) {
        "gogoanime" -> 2
        "hidive" -> 1
        "vuighe" -> 2
        else -> 3 // animevietsub
    }

/**
 * Placeholder for the detail header while the anime is being upgraded from its
 * Lite record, drawn in the shape of the real header so the swap to real content
 * does not shift the rows below it.
 *
 * Every block below corresponds to something the header really renders, and the
 * sizes are taken from that code: a 17sp/22sp title line, a 14sp meta line, an
 * author/studio line, the quality/year/updated-to badge flow, the rating line
 * with its star, and the genre line. The counts are deliberately fixed rather
 * than derived from the Lite card — a skeleton that mirrors the shape of the data
 * it stands in for is the point, and the Lite card has no genres, no studio and no
 * seasons to mirror.
 */
@Composable
private fun AnimeDetailHeaderSkeleton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_PADDING_HORIZONTAL, vertical = 10.dp),
    ) {
        // Title row + the trailing chevron
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .shimmerEffect(shape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(20.dp).shimmerEffect(shape))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // "1.7M views • Tập tiếp theo..." line
        Box(modifier = Modifier.fillMaxWidth(0.45f).height(14.dp).shimmerEffect(shape))

        Spacer(modifier = Modifier.height(18.dp))

        // Author " | " Studio
        Box(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp).shimmerEffect(shape))

        Spacer(modifier = Modifier.height(8.dp))

        // Quality / year / updated-to badges
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.width(38.dp).height(18.dp).shimmerEffect(RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.width(48.dp).height(22.dp).shimmerEffect(RoundedCornerShape(6.dp)))
            Box(modifier = Modifier.width(64.dp).height(22.dp).shimmerEffect(RoundedCornerShape(6.dp)))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Rating, its count and the "season of" link
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(22.dp).height(14.dp).shimmerEffect(shape))
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.width(46.dp).height(14.dp).shimmerEffect(shape))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Genres
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { index ->
                Box(
                    modifier =
                        Modifier
                            // Ragged right edge, the way a genre line wraps.
                            .width((58 + index * 14).dp)
                            .height(14.dp)
                            .shimmerEffect(shape),
                )
            }
        }
    }
}
