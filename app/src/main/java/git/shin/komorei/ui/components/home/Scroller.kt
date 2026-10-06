package git.shin.komorei.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Link
import git.shin.komorei.ui.components.AnimeCard
import git.shin.komorei.ui.components.SectionHeader

/**
 * The `Scroller` home component (mirrors the runner + the Aidoku reference):
 * a small horizontal rail of anime **[AnimeCard]s** — the app's classic item
 * (2:3 poster with quality / episode / rating overlays + title + year/genre).
 * Light and quick to scan; tap navigates to the anime.
 */
@Composable
fun ScrollerRow(
    title: String?,
    entries: List<Link>,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    getSourceName: (String) -> String = { it },
    onSeeAll: (() -> Unit)? = null,
) {
    val animes = entries.mapNotNull { it.anime }
    if (animes.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title ?: "", onSeeAll = onSeeAll)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = animes, key = { "${it.sourceId}:${it.id}" }) { anime ->
                AnimeCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    modifier = Modifier.width(110.dp),
                    getSourceName = getSourceName,
                )
            }
        }
    }
}
