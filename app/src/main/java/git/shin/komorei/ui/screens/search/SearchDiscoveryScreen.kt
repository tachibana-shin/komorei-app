package git.shin.komorei.ui.screens.search

import git.shin.komorei.ui.components.search.CompactInput
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.AnimeCard
import git.shin.komorei.ui.components.GenreGridCard
import git.shin.komorei.ui.components.ListingGridSkeleton
import git.shin.komorei.ui.components.search.DiscoverFilterHeaderRow
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.NeonViolet
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

/**
 * Khám Phá tab — an Aidoku-style global search: a search bar, the three global
 * filter pills (Xếp hạng nội dung / Ngôn ngữ / Nguồn) and a flat 3-column
 * results grid shared with the per-source search. The idle state keeps the
 * genre discovery grid; tapping a genre starts a genre-filtered search.
 */
@Composable
fun SearchDiscoveryScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val contentRating by viewModel.contentRating.collectAsState()
    val language by viewModel.language.collectAsState()
    val sourceFilter by viewModel.sourceFilter.collectAsState()
    val searchUiState by viewModel.searchUiState.collectAsState()
    val genres = viewModel.genres
    val sources = viewModel.sources

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .testTag("search_discovery_screen")
    ) {
        // Search Header Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.search_header_title),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.search_header_subtitle),
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-source Search Bar with OutlinedTextField
            CompactInput(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                hint = stringResource(R.string.search_hint),
                leadingIcon = Icons.Default.Search,
                showClear = searchQuery.isNotEmpty() || selectedGenre != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input_field"),
            )
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
                // Discovery Categories & Genre Grid
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.genres_category_title),
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item {
                        // Visual 2-Column Genre Cards
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 120.dp)
                        ) {
                            val chunkedGenres = genres.chunked(2)
                            chunkedGenres.forEach { pair ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    pair.forEach { genre ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            GenreGridCard(
                                                genre = genre,
                                                isSelected = selectedGenre?.id == genre.id,
                                                onClick = { viewModel.selectGenre(genre) }
                                            )
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is SearchUiState.Loading -> {
                ListingGridSkeleton(
                    modifier = Modifier.fillMaxSize()
                )
            }

            is SearchUiState.Success -> {
                if (state.totalCount == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_results_title),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.no_results_subtitle),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    // Flat 3-column grid like the per-source search — results
                    // from all matched sources merged into one scrollable.
                    val flatResults = state.resultsBySource.values.flatten()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("discover_search_grid")
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(R.string.discover_results_count, state.totalCount),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        items(flatResults, key = { it.id }) { anime ->
                            AnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) },
                                getSourceName = viewModel::getSourceName,
                            )
                        }
                    }
                }
            }

            is SearchUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        color = AnimeRed,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        onClick = { viewModel.retrySearch() },
                        color = AnimeRedContainer,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_retry),
                            color = AnimeRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}