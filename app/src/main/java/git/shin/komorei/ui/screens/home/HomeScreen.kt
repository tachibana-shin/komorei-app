package git.shin.komorei.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Listing
import git.shin.komorei.ui.components.SourceIcon
import git.shin.komorei.ui.components.SourceTabBar
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.TvInitialFocus
import git.shin.komorei.ui.tv.tvFocus
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenListing: (sourceId: String, listing: Listing) -> Unit = { _, _ -> },
    onOpenSearch: (sourceId: String) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val sources by viewModel.sources.collectAsState()
    val sourceDataMap by viewModel.sourceDataMap.collectAsState()
    val listingStateMap by viewModel.listingStateMap.collectAsState()
    val refreshingIds by viewModel.refreshingIds.collectAsState()

    // TV: land initial focus on the FIRST source tab (left/right switches
    // source, down drops into that source's content). No-op on touch devices.
    val sourceTabFocusRequester = remember { FocusRequester() }
    TvInitialFocus(sourceTabFocusRequester)

    val sourcePagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { sources.size }
    )
    val currentSourceIndex = sourcePagerState.currentPage
    val activeSource = sources.getOrNull(currentSourceIndex) ?: return

    // YouTube-style collapsible header & sticky tabbar state
    var isHeaderVisible by remember { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15f) {
                    // Scrolling DOWN -> Hide top header logo, keep source tabbar sticky
                    isHeaderVisible = false
                } else if (available.y > 15f) {
                    // Scrolling UP -> Reveal top header logo smoothly
                    isHeaderVisible = true
                }
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection)
            .testTag("home_screen")
    ) {
        // Collapsible Top Brand Row (Collapses on scroll down like YouTube)
        AnimatedVisibility(
            visible = isHeaderVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = stringResource(R.string.cd_app_logo),
                    tint = AnimeRed,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.brand_komo),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = stringResource(R.string.brand_rei),
                    color = AnimeRed,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                // Active Source indicator badge with Icon widget
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = AnimeRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    SourceIcon(
                        source = activeSource,
                        contentDescription = activeSource.name,
                        fallbackTint = AnimeRed,
                        iconSize = 13.dp,
                        fallbackIconSize = 13.dp,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeSource.name,
                        color = AnimeRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Notification inbox (moved off the bottom bar into the Home header)
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        // TV focus highlight (no-op on phones).
                        .tvFocus(shape = CircleShape, scale = 1.15f)
                        .size(32.dp)
                        .testTag("home_notifications_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = stringResource(R.string.notifications_title),
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // STICKY TABBAR: Luôn ghim ở đầu màn hình khi cuộn nội dung
        SourceTabBar(
            sources = sources,
            selectedIndex = currentSourceIndex,
            firstTabFocusRequester = sourceTabFocusRequester,
            onTabSelected = { index, _ ->
                coroutineScope.launch {
                    sourcePagerState.animateScrollToPage(index)
                }
            }
        )

        // HORIZONTAL PAGER: Vuốt ngang chuyển nguồn
        HorizontalPager(
            state = sourcePagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("home_source_pager")
        ) { pageIndex ->
            val source = sources[pageIndex]
            val sourceData = sourceDataMap[source.id] ?: SourceHomeData(isLoading = true)
            val listingState = listingStateMap[source.id] ?: SourceListingState()

            // Load this source's dynamic listings (Aidoku chip row) once its page
            // is on screen; cached after the first success.
            LaunchedEffect(pageIndex, currentSourceIndex) {
                if (pageIndex == currentSourceIndex) viewModel.loadListings(source.id)
            }

            SourceHomeContent(
                source = source,
                sourceData = sourceData,
                listingState = listingState,
                onSelectListing = { viewModel.selectListing(source.id, it) },
                onRetryHome = { viewModel.loadSourceData(source.id) },
                onLoadListingReset = { viewModel.loadListingPage(source.id, reset = true) },
                onLoadListingMore = { viewModel.loadListingMore(source.id) },
                onOpenListing = { listing -> onOpenListing(source.id, listing) },
                onAnimeClick = onAnimeClick,
                getSourceName = { viewModel.getSourceName(it) },
                onOpenSearch = onOpenSearch,
                isRefreshing = refreshingIds.contains(source.id),
                onRefresh = { viewModel.refreshSource(source.id) },
            )
        }
    }
}