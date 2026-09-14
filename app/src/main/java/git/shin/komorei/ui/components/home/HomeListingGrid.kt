package git.shin.komorei.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.AnimeCard
import git.shin.komorei.ui.components.ListingGridSkeleton
import git.shin.komorei.ui.components.ShimmerLoadingRow
import git.shin.komorei.ui.screens.home.ListingPageState
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.TextMuted
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The paged grid that replaces a source's home content when its listing chip
 * is selected (Aidoku `SourceHomeContentView` listing page behavior): a fixed
 * 3-column [LazyVerticalGrid] of [AnimeCard]s with infinite scroll driven by
 * [onLoadMore], plus loading / error-retry / empty / end-of-list states.
 */
@Composable
fun HomeListingGrid(
    page: ListingPageState,
    onAnimeClick: (Anime) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // First-page load failure with nothing to show yet.
        page.error != null && page.items.isEmpty() -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.listing_load_error),
                    color = AnimeRed,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onRetry,
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

        page.isLoading && page.items.isEmpty() -> ListingGridSkeleton(
            modifier = modifier.fillMaxSize(),
        )

        page.items.isEmpty() -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.listing_empty),
                color = TextMuted,
                fontSize = 14.sp,
            )
        }

        else -> {
            val gridState = rememberLazyGridState()
            LaunchedEffect(gridState) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                    .distinctUntilChanged()
                    .collect { lastIndex ->
                        val total = gridState.layoutInfo.totalItemsCount
                        if (total > 0 && lastIndex >= total - 6) onLoadMore()
                    }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier.fillMaxSize().testTag("home_listing_grid"),
            ) {
                items(page.items, key = { it.id }) { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime) },
                    )
                }
                if (page.isLoadingMore) {
                    item(key = "_load_more") {
                        ShimmerLoadingRow()
                    }
                }
                if (!page.isLoadingMore && !page.hasNextPage) {
                    item(key = "_end") {
                        Text(
                            text = stringResource(R.string.listing_end_of_list),
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}