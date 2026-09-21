package git.shin.komorei.data

import git.shin.komorei.model.Anime
import git.shin.komorei.model.Source

/**
 * One source's outcome in an Aidoku-style streaming multi-source search
 * ([AnimeRepository.searchMultiSourceStream]). Every candidate source is
 * queried concurrently on its own thread and reported independently — a slow or
 * broken source surfaces as its own [SourceSearchEvent.Failed] event instead of
 * taking the whole tab down with it, and the ones that already finished are
 * never blocked by it.
 */
sealed interface SourceSearchEvent {
    /** A source that loaded and ran its search (results may still be empty). */
    data class Completed(val source: Source, val results: List<Anime>) : SourceSearchEvent

    /** A source that could not be loaded / ran (message carries the reason). */
    data class Failed(val source: Source, val message: String) : SourceSearchEvent
}
