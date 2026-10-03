package git.shin.komorei.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.HomeComponentValue
import git.shin.komorei.model.LinkValue
import git.shin.komorei.model.Listing
import git.shin.komorei.model.Source
import git.shin.komorei.ui.components.BannerCarouselSkeleton
import git.shin.komorei.ui.components.SectionSkeleton
import git.shin.komorei.ui.components.home.AnimeEpisodeListRow
import git.shin.komorei.ui.components.home.AnimeListRow
import git.shin.komorei.ui.components.home.BigScrollerRow
import git.shin.komorei.ui.components.home.FiltersRow
import git.shin.komorei.ui.components.home.HomeListingGrid
import git.shin.komorei.ui.components.home.ImageScrollerRow
import git.shin.komorei.ui.components.home.LinksRow
import git.shin.komorei.ui.components.home.ListingChipsRow
import git.shin.komorei.ui.components.home.ListingChipsSkeleton
import git.shin.komorei.ui.components.home.ScrollerRow
import git.shin.komorei.ui.components.search.SourceSearchButton
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.AnimeRedContainer
import git.shin.komorei.ui.theme.TextMuted

/**
 * The listings chips header (Aidoku "get_dynamic_listings"): skeleton while
 * loading, then the chip rail. Both states OVERLAY in a Box so the cross-fade
 * never stacks them vertically (a Column would momentarily double the header
 * height and look blank in the fade window). It is a standalone composable so
 * the `AnimatedVisibility` calls resolve to the top-level overload — inside the
 * header `Row` scope they'd pick `RowScope.AnimatedVisibility` and hit
 * DSL_SCOPE_VIOLATION once a `modifier` is passed.
 */
@Composable
private fun ListingChipsHeader(
    listingsLoading: Boolean,
    listings: List<Listing>,
    selectedIndex: Int,
    onSelectListing: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = listingsLoading && listings.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListingChipsSkeleton()
        }
        AnimatedVisibility(
            visible = listings.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ListingChipsRow(
                listings = listings,
                selectedIndex = selectedIndex,
                onSelect = onSelectListing,
            )
        }
    }
}

/**
 * The per-source browse content shared by the Home tab's pager page and the
 * per-source home screen (`SourceHomeScreen`): the listings chip row (Aidoku
 * "get_dynamic_listings") + the source's full home layout (or the selected
 * listing's paged grid), with loading (shimmer) / error / empty states.
 *
 * State (home layout + listing pages) is passed in — both hosts drive the same
 * rendering through the same `HomeViewModel` per-source methods, so the Home
 * tab and the source screen can never diverge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHomeContent(
    source: Source,
    sourceData: SourceHomeData,
    listingState: SourceListingState,
    onSelectListing: (Int) -> Unit,
    onRetryHome: () -> Unit,
    onLoadListingReset: () -> Unit,
    onLoadListingMore: () -> Unit,
    onOpenListing: (Listing) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    getSourceName: (String) -> String,
    onOpenSearch: (sourceId: String) -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Aidoku listings header: [🔍] [Trang chủ] + get_dynamic_listings chips.
        // The search button sits fixed before the chips rail (the aggregator
        // "all" has no own search — it aggregates other sources' results).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!source.isAggregator) {
                SourceSearchButton(
                    onClick = { onOpenSearch(source.id) },
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Overlay (Box), NOT a stacked Column: during the skeleton→chips swap
            // BOTH AnimatedVisibility children are composed at once, and in a
            // Column they stack row-on-row → the header momentarily doubles in
            // height and looks blank in the fade window. Overlaying them keeps
            // the bar at a constant height while they cross-fade in place.
            // (Hoisted into a private composable below: called straight from the
            // Row scope, AnimatedVisibility resolves to the RowScope overload
            // and DSL_SCOPE_VIOLATION fires once a `modifier` is passed.)
            ListingChipsHeader(
                listingsLoading = listingState.listingsLoading,
                listings = listingState.listings,
                selectedIndex = listingState.selectedIndex,
                onSelectListing = onSelectListing,
                modifier = Modifier.weight(1f),
            )
        }

        // Aidoku listings header content: animate the home↔listing swap.
        // Wrapped in PullToRefreshBox so a pull-down anywhere on the page
        // (home layout, listing grid, …) re-fetches the source.
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f),
        ) {
            Crossfade(
                targetState = listingState.selectedIndex,
                label = "home_content_swap",
                modifier = Modifier.fillMaxSize(),
            ) { selection ->
                when {
                    // Load failed — show error + retry instead of an infinite skeleton / blank page.
                    sourceData.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.home_source_error),
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(AnimeRedContainer)
                                            .clickable(onClick = onRetryHome)
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_retry),
                                        color = AnimeRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    sourceData.isLoading && sourceData.home.isEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp),
                        ) {
                            item { BannerCarouselSkeleton() }
                            items(3) { SectionSkeleton() }
                        }
                    }

                    // Loaded successfully but the source has nothing to show.
                    sourceData.home.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.home_source_empty),
                                color = TextMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    // A listing chip is active — swap the content below to the listing.
                    selection > 0 && listingState.listings.isNotEmpty() -> {
                        HomeListingGrid(
                            page = listingState.page,
                            onAnimeClick = onAnimeClick,
                            onLoadMore = onLoadListingMore,
                            onRetry = onLoadListingReset,
                            isRefreshing = isRefreshing,
                            onRefresh = onRefresh,
                        )
                    }

                    else -> {
                        // Render the source's FULL home layout — a lossless mirror of
                        // the runner's get_home: one dedicated composable per variant.
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp),
                        ) {
                            sourceData.home.forEachIndexed { index, comp ->
                                val baseKey = "${source.id}_${(comp.title ?: "").ifEmpty { index.toString() }}"
                                when (val v = comp.value) {
                                    is HomeComponentValue.ImageScroller ->
                                        if (v.links.isNotEmpty()) {
                                            item(key = "${baseKey}_image") {
                                                ImageScrollerRow(
                                                    title = comp.title,
                                                    links = v.links,
                                                    autoScrollInterval = v.autoScrollInterval,
                                                    onAnimeClick = onAnimeClick,
                                                )
                                            }
                                        }

                                    is HomeComponentValue.BigScroller ->
                                        if (v.entries.isNotEmpty()) {
                                            item(key = "${baseKey}_big") {
                                                BigScrollerRow(
                                                    entries = v.entries,
                                                    autoScrollInterval = v.autoScrollInterval,
                                                    onAnimeClick = onAnimeClick,
                                                )
                                            }
                                        }

                                    is HomeComponentValue.Scroller ->
                                        if (v.entries.isNotEmpty()) {
                                            item(key = "${baseKey}_scroller") {
                                                ScrollerRow(
                                                    title = comp.title,
                                                    entries = v.entries,
                                                    onAnimeClick = onAnimeClick,
                                                    getSourceName = getSourceName,
                                                    onSeeAll =
                                                        v.listing?.let { l ->
                                                            { onOpenListing(l) }
                                                        },
                                                )
                                            }
                                        }

                                    is HomeComponentValue.AnimeEpisodeList ->
                                        if (v.entries.isNotEmpty()) {
                                            item(key = "${baseKey}_episodes") {
                                                AnimeEpisodeListRow(
                                                    title = comp.title,
                                                    entries = v.entries,
                                                    pageSize = v.pageSize,
                                                    onAnimeClick = onAnimeClick,
                                                    onSeeAll =
                                                        v.listing?.let { l ->
                                                            { onOpenListing(l) }
                                                        },
                                                )
                                            }
                                        }

                                    is HomeComponentValue.AnimeList ->
                                        if (v.entries.isNotEmpty()) {
                                            item(key = "${baseKey}_list") {
                                                AnimeListRow(
                                                    title = comp.title,
                                                    entries = v.entries,
                                                    ranking = v.ranking,
                                                    pageSize = v.pageSize,
                                                    onAnimeClick = onAnimeClick,
                                                    onSeeAll =
                                                        v.listing?.let { l ->
                                                            { onOpenListing(l) }
                                                        },
                                                )
                                            }
                                        }

                                    is HomeComponentValue.Filters ->
                                        if (v.items.isNotEmpty()) {
                                            item(key = "${baseKey}_filters") {
                                                FiltersRow(
                                                    title = comp.title,
                                                    items = v.items,
                                                )
                                            }
                                        }

                                    is HomeComponentValue.Links ->
                                        if (v.links.isNotEmpty()) {
                                            item(key = "${baseKey}_links") {
                                                LinksRow(
                                                    title = comp.title,
                                                    links = v.links,
                                                    onLinkClick = { link ->
                                                        when (val lv = link.value) {
                                                            is LinkValue.Listing -> onOpenListing(lv.listing)
                                                            is LinkValue.Anime -> onAnimeClick(lv.anime)
                                                            // LinkValue.Url / null links stay display-only for now.
                                                            else -> Unit
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
