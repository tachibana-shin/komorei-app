package git.shin.komorei.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.home.ListingChipsSkeleton
import git.shin.komorei.ui.components.search.SearchResultsGrid
import git.shin.komorei.ui.components.search.filters.FilterHeaderRow
import git.shin.komorei.ui.components.search.filters.FilterListSheet
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

/**
 * The per-source search screen (Aidoku `SearchViewController` + the
 * DynamicFilters `FilterHeaderView`): a YouTube-style dark page with an
 * autofocused search field + Hủy button, the sticky filter header (aggregate
 * sheet button + per-filter dropdown pills) and debounced paginated results
 * filling the content area.
 */
@Composable
fun SourceSearchScreen(
    sourceId: String,
    onAnimeClick: (Anime) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourceSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val filtersLoading by viewModel.filtersLoading.collectAsState()
    val enabledFilters by viewModel.enabledFilters.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        verticalArrangement = Arrangement.Top,
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ── Search header (YouTube-style): field + Hủy ────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.source_search_hint),
                        color = TextMuted,
                        fontSize = 14.sp,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_icon_cd),
                        tint = if (query.isNotBlank()) AnimeRed else TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = viewModel::clearQuery,
                            modifier = Modifier.testTag("source_search_clear"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.search_clear_cd),
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardDark,
                    unfocusedContainerColor = CardDark,
                    focusedBorderColor = AnimeRed,
                    unfocusedBorderColor = CardBorderDark,
                    cursorColor = AnimeRed,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("source_search_input"),
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("source_search_cancel"),
            ) {
                Text(
                    text = stringResource(R.string.source_search_cancel),
                    color = AnimeRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ── Filter header (Aidoku FilterHeaderView): sticky under the bar ──
        if (filtersLoading && filters.isEmpty()) {
            ListingChipsSkeleton(modifier = Modifier.padding(top = 4.dp))
        } else {
            FilterHeaderRow(
                filters = filters,
                enabledFilters = enabledFilters,
                onFilterValueChange = viewModel::setFilterValue,
                onOpenFilterSheet = { showFilterSheet = true },
            )
        }

        // ── Content: idle hint / results ───────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isIdle -> IdleSearchHint(modifier = Modifier.fillMaxSize())
                uiState.items.isNotEmpty() -> Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.source_search_results_count, uiState.items.size),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                    SearchResultsGrid(
                        state = uiState,
                        onAnimeClick = onAnimeClick,
                        onLoadMore = viewModel::loadMore,
                        onRetry = viewModel::retry,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> SearchResultsGrid(
                    state = uiState,
                    onAnimeClick = onAnimeClick,
                    onLoadMore = viewModel::loadMore,
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showFilterSheet) {
        FilterListSheet(
            filters = filters,
            initialEnabled = enabledFilters,
            onApply = viewModel::replaceAllFilters,
            onDismiss = { showFilterSheet = false },
        )
    }
}

/** The pre-search hint (blank query AND no active filters). */
@Composable
private fun IdleSearchHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(CardDark, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = AnimeRed,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.source_search_idle_title),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.source_search_idle_subtitle),
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
        }
    }
}