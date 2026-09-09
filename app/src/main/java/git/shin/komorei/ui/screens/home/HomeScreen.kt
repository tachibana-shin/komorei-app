package git.shin.komorei.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.AnimeCard
import git.shin.komorei.ui.components.AppIcons
import git.shin.komorei.ui.components.BannerCarousel
import git.shin.komorei.ui.components.BannerCarouselSkeleton
import git.shin.komorei.ui.components.SectionSkeleton
import git.shin.komorei.ui.components.SourceTabBar
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val sources = viewModel.sources
    val sourceDataMap by viewModel.sourceDataMap.collectAsState()

    val sourcePagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { sources.size }
    )
    val currentSourceIndex = sourcePagerState.currentPage
    val activeSource = sources.getOrNull(currentSourceIndex) ?: sources.first()

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
                    Icon(
                        imageVector = AppIcons.getSourceIcon(activeSource.id),
                        contentDescription = activeSource.name,
                        tint = AnimeRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeSource.name,
                        color = AnimeRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // STICKY TABBAR: Luôn ghim ở đầu màn hình khi cuộn nội dung
        SourceTabBar(
            sources = sources,
            selectedIndex = currentSourceIndex,
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

            if (sourceData.isLoading && sourceData.featured.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item { BannerCarouselSkeleton() }
                    items(3) { SectionSkeleton() }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Banner Carousel displaying Hot Spotlights with Anime Description
                    if (sourceData.featured.isNotEmpty()) {
                        item {
                            BannerCarousel(
                                featuredList = sourceData.featured,
                                onAnimeClick = onAnimeClick
                            )
                        }
                    }

                    // Sections / Categories for this source
                    sourceData.sections.forEach { (sectionTitle, animeList) ->
                        item(key = "${source.id}_$sectionTitle") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                            ) {
                                // Section Header with Icon
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AppIcons.getCategoryIcon(sectionTitle),
                                        contentDescription = sectionTitle,
                                        tint = AnimeRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = sectionTitle,
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = stringResource(R.string.section_see_all),
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // LazyRow for horizontal anime cards in this category
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(items = animeList, key = { it.id }) { anime ->
                                        AnimeCard(
                                            anime = anime,
                                            onClick = { onAnimeClick(anime) }
                                        )
                                    }
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
