package git.shin.komorei.model

/**
 * Global-search content-rating filter (Aidoku-style), mirroring a source's
 * manifest `contentRating` int: `0` = safe, `> 0` = 18+.
 *
 * [repositoryValue] is what `AnimeRepository.searchMultiSource` receives:
 * `null` = no rating restriction, `0` = safe-only, `1` = 18+-only.
 */
enum class ContentRatingFilter(
    val repositoryValue: Int?,
) {
    ALL(null),
    SAFE(0),
    NSFW(1),
}
