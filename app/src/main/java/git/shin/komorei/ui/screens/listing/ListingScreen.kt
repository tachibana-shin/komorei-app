package git.shin.komorei.ui.screens.listing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.AnimeCard
import git.shin.komorei.ui.components.ShimmerLoadingRow
import git.shin.komorei.ui.components.animeGridColumns
import git.shin.komorei.ui.components.shimmerEffect
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A full-screen, paged listing of anime for a single source listing.
 * Layout: a simple back-header + a vertical grid with infinite scroll.
 * Back press pops the stack — does NOT exit the app (the tab's stack
 * entry stays alive behind it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingScreen(
    onAnimeClick: (Anime) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val gridState = rememberLazyGridState()

    // Infinite scroll: trigger a load when the user approaches the last page.
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.distinctUntilChanged()
            .collect { lastIndex ->
                val total = gridState.layoutInfo.totalItemsCount
                if (lastIndex >= total - 6) viewModel.loadMore()
            }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding(),
    ) {
        ListingTopBar(
            title = viewModel.listingName,
            subtitle = viewModel.sourceName,
            onBack = onBack,
        )

        when {
            uiState.isLoading -> ListingGridSkeleton()

            uiState.items.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text =
                                uiState.error
                                    ?: stringResource(R.string.listing_empty),
                            color = if (uiState.error != null) AnimeRed else TextMuted,
                            fontSize = 14.sp,
                        )
                        if (uiState.error != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                onClick = { viewModel.loadMore(reset = true) },
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

            else ->
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = animeGridColumns()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyVerticalGrid(
                            columns = columns,
                            state = gridState,
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 120.dp, // clear the mini player + bottom nav
                                ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize().testTag("listing_grid"),
                        ) {
                            items(uiState.items, key = { it.id }) { anime ->
                                AnimeCard(
                                    anime = anime,
                                    onClick = { onAnimeClick(anime) },
                                )
                            }
                            if (uiState.isLoadingMore) {
                                item(key = "_load_more") { ListingLoadingRow() }
                            }
                            if (!uiState.isLoadingMore && !uiState.hasNextPage && uiState.items.isNotEmpty()) {
                                item(key = "_end") { ListingEndRow() }
                            }
                        }
                    }
                }
        }
    }
}

// ── top bar ─────────────────────────────────────────────────────────────

@Composable
private fun ListingTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    // TV focus highlight (no-op on phones).
                    .tvFocus(shape = CircleShape, scale = 1.15f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

// ── loading / end row indicators ────────────────────────────────────────

@Composable
private fun ListingLoadingRow() {
    ShimmerLoadingRow()
}

@Composable
private fun ListingEndRow() {
    Text(
        text = stringResource(R.string.listing_end_of_list),
        color = TextMuted,
        fontSize = 12.sp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

// ── shimmer skeleton (initial load) ─────────────────────────────────────

@Composable
private fun ListingGridSkeleton() {
    // 3 columns × 6 rows of anime-card shimmer placeholders. Bounded Column
    // (not LazyColumn) so it can live inside the root Column.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        repeat(2) { _ ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                repeat(3) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f)
                                    .shimmerEffect(RoundedCornerShape(12.dp)),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(13.dp)
                                    .shimmerEffect(RoundedCornerShape(4.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(11.dp)
                                    .shimmerEffect(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
    }
}
