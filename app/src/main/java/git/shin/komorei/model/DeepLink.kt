package git.shin.komorei.model

/**
 * App-side mirror of the runner's `DeepLinkResult` — what a source's
 * `handle_deep_link(url)` decided the URL should open.
 */
sealed class DeepLinkTarget {
    /** Open the source's anime with key [animeKey] (detail + first episode). */
    data class Anime(
        val animeKey: String,
    ) : DeepLinkTarget()

    /** Open the player directly at [episodeKey] of [animeKey]. */
    data class Episode(
        val animeKey: String,
        val episodeKey: String,
    ) : DeepLinkTarget()

    /** Open the source catalog listing with the given [listing] (id + name + kind). */
    data class Listing(
        val listing: git.shin.komorei.model.Listing,
    ) : DeepLinkTarget()
}

/**
 * A [DeepLinkTarget] plus the source that resolved it — deep links are asked of
 * every installed source (Aidoku-style), so routing needs to know which one
 * claimed the URL.
 */
data class ResolvedDeepLink(
    val sourceId: String,
    val target: DeepLinkTarget,
)
