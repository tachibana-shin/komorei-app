package git.shin.komorei.ui.screens.rankings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.SearchResultSkeleton
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.GoldRating
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun RankingsScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RankingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .testTag("rankings_screen")
    ) {
        Text(
            text = stringResource(R.string.rankings_title),
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.rankings_subtitle),
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        val periods = RankingPeriod.entries
        SecondaryTabRow(
            selectedTabIndex = periods.indexOf(state.period),
            containerColor = BackgroundDark,
            contentColor = TextPrimary,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        selectedTabIndex = periods.indexOf(state.period)
                    ),
                    color = AnimeRed,
                    height = 3.dp,
                )
            },
            divider = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            periods.forEach { period ->
                val isSelected = period == state.period
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.selectPeriod(period) },
                    text = {
                        Text(
                            text = stringResource(period.labelRes()),
                            color = if (isSelected) AnimeRed else TextMuted,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.testTag("rankings_period_${period.name.lowercase(Locale.US)}"),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading -> SearchResultSkeleton()
            state.items.isEmpty() -> RankingsEmptyState()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 114.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = state.items,
                    key = { "${it.anime.sourceId}_${it.anime.id}" },
                ) { ranked ->
                    RankingRow(
                        ranked = ranked,
                        sourceName = viewModel.getSourceName(ranked.anime.sourceId),
                        onClick = { onAnimeClick(ranked.anime) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingRow(
    ranked: RankedAnime,
    sourceName: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("ranking_entry_${ranked.anime.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${ranked.rank}",
            color = rankColor(ranked.rank),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        AsyncImage(
            model = ranked.anime.posterUrl,
            contentDescription = ranked.anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(56.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ranked.anime.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sourceName,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ranked.anime.rating?.let { rating ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = GoldRating,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", rating),
                        color = GoldRating,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = stringResource(R.string.rankings_views, formatViews(ranked.anime.views)),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RankingsEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Leaderboard,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.rankings_empty_title),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.rankings_empty_subtitle),
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun rankColor(rank: Int): Color = when (rank) {
    1 -> GoldRating
    2 -> Color(0xFFC0C7D1)
    3 -> Color(0xFFCD7F32)
    else -> TextMuted
}

/** 1234 -> "1.2K", 1_234_567 -> "1.2M". */
internal fun formatViews(views: Int): String = when {
    views >= 1_000_000 -> String.format(Locale.US, "%.1fM", views / 1_000_000.0)
    views >= 1_000 -> String.format(Locale.US, "%.1fK", views / 1_000.0)
    else -> "$views"
}

private fun RankingPeriod.labelRes(): Int = when (this) {
    RankingPeriod.DAY -> R.string.rankings_period_day
    RankingPeriod.WEEK -> R.string.rankings_period_week
    RankingPeriod.MONTH -> R.string.rankings_period_month
}
