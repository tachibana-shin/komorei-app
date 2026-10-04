package git.shin.komorei.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Source
import git.shin.komorei.ui.components.AnimeCard
import git.shin.komorei.ui.components.AnimeCardSkeleton
import git.shin.komorei.ui.components.SourceIcon
import git.shin.komorei.ui.components.search.CompactInput
import git.shin.komorei.ui.components.search.DiscoverFilterHeaderRow
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.NeonViolet
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.TvInitialFocus
import git.shin.komorei.ui.tv.tvFocus

/**
 * Tìm Kiếm tab — an Aidoku-style global search: a search bar, the three
 * global filter pills (Xếp hạng nội dung / Ngôn ngữ / Nguồn) and a per-source
 * results grid. When idle with no query, recent search history is displayed
 * instead of the old genre grid (sources no longer provide genre data).
 */
@Composable
fun SearchDiscoveryScreen(
    onAnimeClick: (Anime) -> Unit,
    onOpenSourceSearch: (sourceId: String, query: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val contentRating by viewModel.contentRating.collectAsState()
    val language by viewModel.language.collectAsState()
    val sourceFilter by viewModel.sourceFilter.collectAsState()
    val searchUiState by viewModel.searchUiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val sources by viewModel.sources.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding()
                .testTag("search_discovery_screen"),
    ) {
        // Search Header Bar: the "Tìm Kiếm" branding collapses with an
        // animation as soon as the search field grabs focus — search mode
        // hands the whole area to the results — and a Hủy button slides in
        // next to the field (iOS-style) to exit search mode again. The old
        // "Đa Nguồn" subtitle is gone entirely.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val focusManager = LocalFocusManager.current
            val inputFocusSource = remember { MutableInteractionSource() }
            val inputFocused by inputFocusSource.collectIsFocusedAsState()
            // TV: land initial focus on the search field so D-pad/OK starts
            // typing immediately (YouTube-style). No-op on phones/tablets.
            val searchFocusRequester = remember { FocusRequester() }
            TvInitialFocus(searchFocusRequester)
            AnimatedVisibility(
                visible = !inputFocused,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.search_header_title),
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.testTag("search_header_title"),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Multi-source Search Bar + an animated Hủy that slides in from
            // the right while the field is focused (the input Row recomposes
            // each animation frame so the field grows back smoothly on exit).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CompactInput(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    hint = stringResource(R.string.search_hint),
                    leadingIcon = Icons.Default.Search,
                    showClear = searchQuery.isNotEmpty() || selectedGenre != null,
                    interactionSource = inputFocusSource,
                    focusRequester = searchFocusRequester,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag("search_input_field"),
                )
                AnimatedVisibility(
                    visible = inputFocused,
                    enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.onSearchQueryChange("")
                            },
                            modifier =
                                Modifier
                                    // TV focus highlight (no-op on phones).
                                    .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)
                                    .testTag("search_input_cancel"),
                        ) {
                            Text(
                                text = stringResource(R.string.search_cancel),
                                color = AnimeRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // Aidoku-style global filters (content rating / language / sources)
        // — replaces the old per-genre quick chips.
        DiscoverFilterHeaderRow(
            contentRating = contentRating,
            language = language,
            includedSourceIds = sourceFilter,
            sources = sources,
            onContentRatingChange = viewModel::setContentRating,
            onLanguageChange = viewModel::setLanguage,
            onSourcesChange = viewModel::setSourceFilter,
        )

        // Search Results / Discovery Content
        when (val state = searchUiState) {
            is SearchUiState.Idle -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.search_history_title),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    item {
                        val history = searchHistory
                        if (history.isEmpty()) {
                            // No history — show prompt
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.search_history_empty),
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.search_hint),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        } else {
                            // History list
                            history.forEach { query ->
                                val isLast = query == history.first()
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Re-run the search with this query
                                                viewModel.onSearchQueryChange(query)
                                            }.padding(vertical = 4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = query,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (isLast) {
                                            Text(
                                                text = stringResource(R.string.search_history_latest),
                                                color = AnimeRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(end = 4.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeHistoryItem(query) },
                                            modifier =
                                                Modifier
                                                    // TV focus highlight (no-op on phones).
                                                    .tvFocus(shape = CircleShape, scale = 1.15f)
                                                    .size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.search_history_clear_item),
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            // Clear all history
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { viewModel.clearSearchHistory() },
                                modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                            ) {
                                Text(
                                    text = stringResource(R.string.search_history_clear_all),
                                    color = AnimeRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    // Show the clear-search hint when there's a genre selected
                    if (selectedGenre != null) {
                        item {
                            // Genre badge showing current genre filter
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.filter_genre_label, selectedGenre!!.name),
                                    color = AnimeRed,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            is SearchUiState.Searching -> {
                // Every candidate source renders its own section right away.
                // Sources that already answered show results/errors; the rest
                // keep their own loading shimmer until their event lands.
                SearchSourceSections(
                    sources = state.candidateSources,
                    resultsBySource = state.resultsBySource,
                    sourceErrors = state.sourceErrors,
                    emptySources = state.emptySources,
                    onAnimeClick = onAnimeClick,
                    onOpenSourceSearch = { sourceId -> onOpenSourceSearch(sourceId, searchQuery) },
                    onRetry = { viewModel.retrySearch() },
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshSearch() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SearchUiState.Success -> {
                // Terminal state: every accounted source shows — those with
                // results, those that errored, and those that finished empty
                // ("không có kết quả" in their own body). Only when NO source
                // produced anything does the whole-tab empty state take over.
                val hasAnyOutcome =
                    state.resultsBySource.any { (_, results) -> results.isNotEmpty() } ||
                        state.sourceErrors.isNotEmpty()
                val sourcesShown =
                    if (hasAnyOutcome) {
                        (state.resultsBySource.keys + state.sourceErrors.keys + state.emptySources)
                            .distinct()
                    } else {
                        emptyList()
                    }
                if (sourcesShown.isEmpty()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_results_title),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.no_results_subtitle),
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    SearchSourceSections(
                        sources = sourcesShown,
                        resultsBySource = state.resultsBySource,
                        sourceErrors = state.sourceErrors,
                        emptySources = state.emptySources,
                        showCountHeader = true,
                        totalCount = state.totalCount,
                        onAnimeClick = onAnimeClick,
                        onOpenSourceSearch = { sourceId -> onOpenSourceSearch(sourceId, searchQuery) },
                        onRetry = { viewModel.retrySearch() },
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshSearch() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            is SearchUiState.Error -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.message,
                        color = AnimeRed,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        onClick = { viewModel.retrySearch() },
                        color = AnimeRedContainer,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_retry),
                            color = AnimeRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders one horizontal section per source: a header row with the source's
 * icon + name (tinted red when the source failed) — tappable to jump into
 * that source's own search screen with the current query — and below it
 * either the result cards (horizontal swipe), a visible "không có kết quả"
 * note when the source finished empty, an error + retry row when it failed,
 * or a small loading shimmer row while that source is still resolving.
 *
 * Shared by the live [SearchUiState.Searching] phase (where pending sources
 * keep their shimmer) and the terminal [SearchUiState.Success] phase (which
 * lists every source that produced results, errored, or finished empty).
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchSourceSections(
    sources: List<Source>,
    resultsBySource: Map<Source, List<Anime>>,
    sourceErrors: Map<Source, String>,
    emptySources: Set<Source> = emptySet(),
    onAnimeClick: (Anime) -> Unit,
    onOpenSourceSearch: (sourceId: String) -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
    showCountHeader: Boolean = false,
    totalCount: Int = 0,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("search_results_list"),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showCountHeader) {
                item {
                    Text(
                        text = stringResource(R.string.discover_results_count, totalCount),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
            sources.forEach { source ->
                val animes = resultsBySource[source] ?: emptyList()
                val error = sourceErrors[source]

                // Section header with source icon. The whole row is tappable and
                // jumps to this source's own search screen with the current query.
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenSourceSearch(source.id) }
                                .padding(top = 4.dp, bottom = 4.dp, end = 4.dp, start = 2.dp),
                    ) {
                        SourceIcon(
                            source = source,
                            contentDescription = null,
                            fallbackTint = if (error != null) AnimeRed else TextPrimary,
                            iconSize = 22.dp,
                            fallbackIconSize = 22.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = source.name,
                            color = if (error != null) AnimeRed else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.source_search_entry_cd),
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                // Source body: results (horizontal swipe), pending shimmer, or error.
                item {
                    when {
                        animes.isNotEmpty() -> {
                            // Compact cards matching the home ScrollerRow standard
                            // (110dp) so ~3 fit on a phone row — 160dp cards
                            // squeezed to ~2 per screen and looked oversized.
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(animes) { anime ->
                                    AnimeCard(
                                        anime = anime,
                                        onClick = { onAnimeClick(anime) },
                                        getSourceName = { _ -> source.name },
                                        cardWidth = 110.dp,
                                    )
                                }
                            }
                        }
                        error != null -> {
                            // The section failed to load — a visible error + retry,
                            // not the old 2dp spacer that left the section empty.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = AnimeRed,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.source_search_error),
                                        color = AnimeRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (error.isNotBlank()) {
                                        Text(
                                            text = error,
                                            color = TextMuted,
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 14.sp,
                                            modifier = Modifier.testTag("search_source_error_detail"),
                                        )
                                    }
                                }
                                Surface(
                                    onClick = onRetry,
                                    color = AnimeRedContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.testTag("search_source_retry"),
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_retry),
                                        color = AnimeRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        source in emptySources -> {
                            // Finished with zero matches — say so instead of
                            // silently vanishing (or shimmering forever).
                            Text(
                                text = stringResource(R.string.search_source_empty),
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("search_source_empty"),
                            )
                        }
                        else -> {
                            // Source still resolving — its own loading shimmer row.
                            // A scrollable LazyRow, not a plain Row: three 135dp
                            // skeletons in a fixed Row overflow past the screen and
                            // clip (stuck at ~2 cards, no way to swipe) — unlike
                            // the results row it precedes. Match the results 1:1
                            // with the same compact 110dp cards as ScrollerRow.
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(6) {
                                    AnimeCardSkeleton(modifier = Modifier.width(110.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
