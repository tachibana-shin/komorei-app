package git.shin.komorei.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeWithEpisode
import git.shin.komorei.ui.components.SectionHeader
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.utils.formatTimeAgo
import java.time.Instant

/**
 * The `AnimeEpisodeList` home component (mirrors the runner + the Aidoku
 * reference): rows of "latest update" — cover on the left + anime title +
 * `Tập X • quality` subtitle + relative upload time (distinct from
 * [AnimeListRow]'s rank rows).
 *
 * Two rendering modes, chosen by [pageSize]:
 * - `pageSize == null` → one continuous VERTICAL list.
 * - `pageSize != null` → the paged grid style: each page = a multi-column
 *   grid of compact rows (2 columns on phones, 3 on tablets, 4 on very large
 *   screens — [pagedGridColumns]), up to `pageSize` items.
 *
 * Bounded Column / pager — never a LazyColumn (would nest a vertical
 * scrollable inside the Home tab's LazyColumn → FATAL).
 */
@Composable
fun AnimeEpisodeListRow(
    title: String?,
    entries: List<AnimeWithEpisode>,
    pageSize: Int?,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
) {
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title ?: "", onSeeAll = onSeeAll)

        val page = pageSize?.takeIf { it > 0 }
        if (page != null) {
            val pages = entries.chunked(page)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Same fixed (not weight/percentage) cell width as AnimeList's
                // paged grid — columns chunks rows + packed cells, capped at
                // PAGED_CELL_WIDTH_MAX on the phone's 2-column layout.
                val columns = pagedGridColumns()
                val cellWidth = pagedCellWidth(columns)
                HorizontalPager(
                    state = rememberPagerState(pageCount = { pages.size }),
                    pageSpacing = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { pageIndex ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        pages[pageIndex].chunked(columns).forEachIndexed { rowIndex, rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                rowItems.forEachIndexed { columnIndex, entry ->
                                    AnimeEpisodeListGridCell(
                                        entry = entry,
                                        onAnimeClick = onAnimeClick,
                                        modifier = Modifier.width(cellWidth),
                                    )
                                }
                                // Keep an incomplete last row left-aligned.
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.width(cellWidth))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                entries.forEach { entry ->
                    AnimeEpisodeListRowCell(
                        entry = entry,
                        onAnimeClick = onAnimeClick,
                    )
                }
            }
        }
    }
}

/** Full-width "latest update" row: cover, anime title, episode subtitle, relative time. */
@Composable
private fun AnimeEpisodeListRowCell(
    entry: AnimeWithEpisode,
    onAnimeClick: (Anime) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onAnimeClick(entry.anime) }
                .padding(vertical = 6.dp),
    ) {
        AnimeEpisodeThumb(entry = entry, modifier = Modifier.size(width = 66.dp, height = 92.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        ) {
            Text(
                text = entry.anime.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    stringResource(
                        R.string.home_episode_subtitle,
                        entry.episode.episodeNumber,
                        entry.episode.quality,
                    ),
                color = TextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.episode.dateUploaded?.let { timestamp ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatTimeAgo(Instant.ofEpochMilli(timestamp)),
                    color = TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Compact "row2" cell used by the paged grid (2 phone / 3 tablet / 4 TV). */
@Composable
private fun AnimeEpisodeListGridCell(
    entry: AnimeWithEpisode,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clickable { onAnimeClick(entry.anime) }
                .padding(vertical = 6.dp),
    ) {
        AnimeEpisodeThumb(entry = entry, modifier = Modifier.size(width = 52.dp, height = 74.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
        ) {
            Text(
                text = entry.anime.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    stringResource(
                        R.string.home_episode_subtitle,
                        entry.episode.episodeNumber,
                        entry.episode.quality,
                    ),
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The paired anime's poster thumbnail. */
@Composable
private fun AnimeEpisodeThumb(
    entry: AnimeWithEpisode,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        AsyncImage(
            model = entry.anime.posterUrl,
            contentDescription = entry.anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        QualityTagBadge(qualityTag = entry.anime.qualityTag, inset = 4.dp)
    }
}
