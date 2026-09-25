package git.shin.komorei.ui.components.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.BannerCarousel

/**
 * The `BigScroller` home component (mirrors the runner + the Aidoku
 * reference): the large auto-scrolling hero carousel — banner + title +
 * rating + description + tags — one big entry per page.
 *
 * Rendered via the app's existing [BannerCarousel] (identical layout: big
 * hero cards with rating / quality / episode badges).
 */
@Composable
fun BigScrollerRow(
    entries: List<Anime>,
    autoScrollInterval: Float?,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    // BannerCarousel owns its own auto-scroll cadence (4.5s).
    BannerCarousel(
        featuredList = entries,
        onAnimeClick = onAnimeClick,
        modifier = modifier,
    )
}
