package git.shin.komorei.ui.navigation

import android.net.Uri

sealed class Screen(
    val route: String,
) {
    data object Home : Screen("home")

    data object Search : Screen("search")

    data object Library : Screen("library")

    data object Player : Screen("player/{animeId}/{episodeId}?") {
        /**
         * Builds the player route. IDs are URL-encoded so ids that contain path
         * separators (e.g. "/conan-phan-1/") stay inside a single route segment
         * instead of being split into extra path levels. Consumers must read the
         * argument with `arguments?.getString("animeId")` — Navigation decodes it.
         */
        fun createRoute(
            animeId: String,
            episodeId: String? = null,
        ): String {
            val base = Uri.encode(animeId)
            return if (episodeId != null) "player/$base/${Uri.encode(episodeId)}" else "player/$base"
        }
    }

    data object Category : Screen("category/{filters}") {
        fun createRoute(filtersJson: String): String = "category/$filtersJson"
    }

    data object Listing : Screen("listing/{sourceId}/{listingArg}") {
        /**
         * Builds the listing route. [sourceId] is URL-encoded like the player
         * route; [listingArg] is the URL-safe JSON serialization of the whole
         * [git.shin.komorei.model.Listing] (see [ListingArgCodec]) so the
         * screen can open any source listing without re-resolving it.
         */
        fun createRoute(
            sourceId: String,
            listing: git.shin.komorei.model.Listing,
        ): String = "listing/${Uri.encode(sourceId)}/${ListingArgCodec.encode(listing)}"
    }

    data object Sources : Screen("sources")

    data object SourceRepos : Screen("sources/repos")

    /** In-app notifications (new episodes for followed anime, source messages). */
    data object Notifications : Screen("notifications")

    /** App-wide settings (source repos, caches, about, insights) — distinct from per-source settings. */
    data object Settings : Screen("settings")

    /** The "Nâng cao" hub (server log, network cache, reset) — off Settings. */
    data object Advanced : Screen("advanced")

    /** Aidoku-style "Ghi nhật ký máy chủ" viewer — off Advanced. */
    data object Logs : Screen("logs")

    /** App information and support links (Aidoku's About page). */
    data object About : Screen("about")

    /** Reading activity statistics (Aidoku's Insights page). */
    data object Insights : Screen("insights")

    /** Local backups and Google Drive backup/restore. */
    data object Backups : Screen("backups")

    /**
     * A single source's "home screen" (Aidoku's NewSourceViewController): the
     * source's listings + full home layout in its own full-screen page, with a
     * top-bar ⋮ menu (Cài đặt / Mở trang web). [sourceId] is URL-encoded like
     * the player/listing routes.
     */
    data object SourceHome : Screen("source_home/{sourceId}") {
        fun createRoute(sourceId: String): String = "source_home/${Uri.encode(sourceId)}"
    }

    /** The per-source settings screen (dynamic settings + website + cache). */
    data object SourceSettings : Screen("source_settings/{sourceId}") {
        fun createRoute(sourceId: String): String = "source_settings/${Uri.encode(sourceId)}"
    }

    /**
     * The per-source search screen (Aidoku SearchViewController + the
     * DynamicFilters UI): a YouTube-style dark page with a search field + the
     * filter header (aggregate sheet button + per-filter dropdown pills) and
     * debounced, paginated results. [sourceId] is URL-encoded like the other
     * source routes.
     *
     * The optional query arguments carry the ACTIVE search — `query` + a
     * URL-safe JSON of the enabled [git.shin.komorei.model.FilterValue]s (see
     * [SearchArgsCodec]) — so a search in progress rides the URL (deep-linkable
     * and restored across process death). Navigation percent-decodes query
     * arguments when matching.
     */
    data object SourceSearch : Screen("source_search/{sourceId}?query={query}&filters={filters}") {
        fun createRoute(sourceId: String): String = createRoute(sourceId, "", emptyList())

        fun createRoute(
            sourceId: String,
            query: String,
            filters: List<git.shin.komorei.model.FilterValue>,
        ): String {
            val base = Uri.encode(sourceId)
            val q = SearchArgsCodec.encodeQuery(query)
            val f = SearchArgsCodec.encodeFilters(filters)
            return "source_search/$base?query=$q&filters=$f"
        }
    }

    /**
     * A real in-app WebView browser for a source's website. [sourceId] and
     * [url] are both URL-encoded like the other source routes. Logging in here
     * writes into the shared CookieManager that backs [git.shin.komorei.data.network.WebViewCookieJar],
     * so every media request from that source automatically carries the session.
     */
    data object SourceBrowser : Screen("source_browser/{sourceId}/{url}") {
        fun createRoute(
            sourceId: String,
            url: String,
        ): String = "source_browser/${Uri.encode(sourceId)}/${Uri.encode(url)}"
    }
}
