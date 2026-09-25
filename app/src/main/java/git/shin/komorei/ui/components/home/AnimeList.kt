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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Link
import git.shin.komorei.ui.components.SectionHeader
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

/**
 * The `AnimeList` home component (mirrors the runner + the Aidoku reference).
 *
 * Two rendering modes, chosen by [pageSize]:
 * - `pageSize == null` → a VERTICAL list of rows — small cover on the left,
 *   title (2 lines) + subtitle on the right, and a **rank number** on the far
 *   left when [ranking] is true.
 * - `pageSize != null` → the paged style (like Aidoku's `mangaListLayout`):
 *   a horizontally-paged layout where each page = a **multi-column grid**
 *   (2 columns on phones, 3 on tablets, 4 on very large screens — see
 *   [pagedGridColumns]) of compact cells holding up to `pageSize` items,
 *   each cell a FIXED width — computed once from the measured container
 *   (`cellWidth`), capped at [PAGED_CELL_WIDTH_MAX] on phones, never a
 *   percentage/weight — so the columns stay **packed** with no leftover
 *   black gap at any screen size.
 *   Rank numbers keep counting across pages, row-major.
 *
 * Bounded Column / pager — never a LazyColumn (would nest a vertical
 * scrollable inside the Home tab's LazyColumn → FATAL).
 */
@Composable
fun AnimeListRow(
    title: String?,
    entries: List<Link>,
    ranking: Boolean,
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
                                    AnimeListGridCell(
                                        index = pageIndex * page + rowIndex * columns + columnIndex,
                                        entry = entry,
                                        ranking = ranking,
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
                entries.forEachIndexed { index, entry ->
                    AnimeListRowCell(
                        index = index,
                        entry = entry,
                        ranking = ranking,
                        onAnimeClick = onAnimeClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Ranked row (or unranked when [ranking] false): rank, cover, title, subtitle. */
@Composable
private fun AnimeListRowCell(
    index: Int,
    entry: Link,
    ranking: Boolean,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anime = entry.anime
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                // TV focus highlight (no-op on phones) — full-width row, ring only.
                .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.0f)
                .clickable(enabled = anime != null) {
                    if (anime != null) onAnimeClick(anime)
                }.padding(vertical = 6.dp),
    ) {
        if (ranking) {
            Text(
                text = "${index + 1}",
                color = if (index < 3) AnimeRed else TextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .width(22.dp)
                        .padding(end = 6.dp),
            )
        }
        AnimeThumb(entry = entry, modifier = Modifier.size(width = 66.dp, height = 92.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        ) {
            Text(
                text = entry.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            entry.subtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Compact cell used by the paged grid (2 columns phone / 3 tablet / 4 TV). */
@Composable
private fun AnimeListGridCell(
    index: Int,
    entry: Link,
    ranking: Boolean,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anime = entry.anime
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                // TV focus highlight (no-op on phones) — grid cell.
                .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.02f)
                .clickable(enabled = anime != null) {
                    if (anime != null) onAnimeClick(anime)
                }.padding(vertical = 6.dp),
    ) {
        if (ranking) {
            Text(
                text = "${index + 1}",
                color = if (index < 3) AnimeRed else TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .padding(end = 4.dp),
            )
        }
        AnimeThumb(entry = entry, modifier = Modifier.size(width = 52.dp, height = 74.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
        ) {
            Text(
                text = entry.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            entry.subtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The link's poster / cover thumbnail, or the linked anime's poster. */
@Composable
private fun AnimeThumb(
    entry: Link,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        AsyncImage(
            model = entry.imageUrl ?: entry.anime?.posterUrl,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        QualityTagBadge(qualityTag = entry.anime?.qualityTag, inset = 4.dp)
    }
}
