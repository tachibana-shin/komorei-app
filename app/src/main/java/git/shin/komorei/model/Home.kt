package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/*
 * Full, lossless app-side mirror of the komorei runner's `get_home` result.
 *
 * The runner (wasm source) exposes its home page as a list of
 * [HomeComponent]s — one per UI row. Mapping every variant (incl. the
 * image-only `ImageScroller`, `Filters` and `Links` rows) means the app can
 * render whatever a source returns without dropping data at the boundary.
 */

/** How a [Listing] should be displayed (mirrors the runner `ListingKind`). */
enum class ListingKind { DEFAULT, LIST }

/**
 * A named, filterable listing ("Mới nhất", "Phổ biến", ...) carried by links
 * and components. Moshi-serializable so it can travel as a navigation route
 * argument (see `ui/navigation/ListingArgCodec`).
 */
@JsonClass(generateAdapter = true)
data class Listing(
    val id: String,
    val name: String,
    val kind: ListingKind = ListingKind.DEFAULT,
)

/** What tapping a [Link] navigates to (mirrors the runner `LinkValue`). */
sealed class LinkValue {
    data class Url(
        val url: String,
    ) : LinkValue()

    data class Listing(
        val listing: git.shin.komorei.model.Listing,
    ) : LinkValue()

    data class Anime(
        val anime: git.shin.komorei.model.Anime,
    ) : LinkValue()
}

/** A link used inside home components (image scrollers, rails, header rows, ...). */
data class Link(
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val value: LinkValue? = null,
) {
    /** Convenience: the linked anime when this link points at one. */
    val anime: Anime? get() =
        when (val v = value) {
            is LinkValue.Anime -> v.anime
            else -> null
        }
}

/** A link to a filtered listing (entries of the `Filters` component). */
data class FilterItem(
    val title: String,
    val values: List<FilterValue>? = null,
)

/** A paired anime + episode (entries of the `AnimeEpisodeList` component). */
data class AnimeWithEpisode(
    val anime: Anime,
    val episode: Episode,
)

/** One row of a source's home layout. */
data class HomeComponent(
    val title: String? = null,
    val subtitle: String? = null,
    val value: HomeComponentValue,
)

/** The value of a home component: the FULL `get_home` data, in order. */
sealed class HomeComponentValue {
    /** A horizontal automatic-scrolling scroller of images (only link images/values used). */
    data class ImageScroller(
        val links: List<Link>,
        val autoScrollInterval: Float? = null,
        val width: Int? = null,
        val height: Int? = null,
    ) : HomeComponentValue()

    /** A large hero scroller of anime (title, authors, cover, description, rating, tags). */
    data class BigScroller(
        val entries: List<Anime>,
        val autoScrollInterval: Float? = null,
    ) : HomeComponentValue()

    /** A small horizontal rail of anime links. */
    data class Scroller(
        val entries: List<Link>,
        val listing: Listing? = null,
    ) : HomeComponentValue()

    /** A ranked / plain list of anime links ("top" rows). */
    data class AnimeList(
        val ranking: Boolean,
        val pageSize: Int? = null,
        val entries: List<Link>,
        val listing: Listing? = null,
    ) : HomeComponentValue()

    /** A list of anime with their latest episode ("recent updates" rows). */
    data class AnimeEpisodeList(
        val pageSize: Int? = null,
        val entries: List<AnimeWithEpisode>,
        val listing: Listing? = null,
    ) : HomeComponentValue()

    /** Links to filtered listings (genre chips etc). */
    data class Filters(
        val items: List<FilterItem>,
    ) : HomeComponentValue()

    /** A plain list of links (external urls, "view more", ...). */
    data class Links(
        val links: List<Link>,
    ) : HomeComponentValue()
}
