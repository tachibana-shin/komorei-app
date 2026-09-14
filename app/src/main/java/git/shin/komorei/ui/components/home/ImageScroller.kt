package git.shin.komorei.ui.components.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Link
import git.shin.komorei.ui.components.SectionHeader
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The `ImageScroller` home component (mirrors the runner + the Aidoku
 * reference): a horizontal strip of bare banner images (no text overlay),
 * auto-scrolling on [autoScrollInterval] when set. Tapping a link navigates
 * to the linked anime (url/listing links are display-only for now).
 */
@Composable
fun ImageScrollerRow(
    title: String?,
    links: List<Link>,
    autoScrollInterval: Float?,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) return

    // Banner-ish card: 3:2-ish strip, fixed height like the source's default.
    val itemHeight = 130.dp
    val itemWidth = (itemHeight * 1.5f).coerceAtLeast(180.dp)
    val listState: LazyListState = rememberLazyListState()

    LaunchedEffect(listState, links.size, autoScrollInterval) {
        val intervalMs = (autoScrollInterval ?: 4f)
            .coerceAtLeast(1.5f)
            .let { (it * 1000).toLong() }
        while (links.isNotEmpty()) {
            delay(intervalMs.milliseconds)
            val next = (listState.firstVisibleItemIndex.coerceAtLeast(0) + 1) % links.size
            listState.animateScrollToItem(next)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title ?: "")

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items = links, key = { _, link -> link.title + (link.imageUrl ?: "") }) { _, link ->
                val anime = link.anime
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .width(itemWidth)
                        .height(itemHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = anime != null) { if (anime != null) onAnimeClick(anime) }
                ) {
                    AsyncImage(
                        model = link.imageUrl,
                        contentDescription = link.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    QualityTagBadge(qualityTag = link.anime?.qualityTag)
                }
            }
        }
    }
}