package git.shin.komorei.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
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
        fun createRoute(animeId: String, episodeId: String? = null): String {
            val base = Uri.encode(animeId)
            return if (episodeId != null) "player/$base/${Uri.encode(episodeId)}" else "player/$base"
        }
    }

    data object Category : Screen("category/{filters}") {
        fun createRoute(filtersJson: String): String {
            return "category/$filtersJson"
        }
    }

    data object Listing : Screen("listing/{sourceId}/{listingArg}") {
        /**
         * Builds the listing route. [sourceId] is URL-encoded like the player
         * route; [listingArg] is the URL-safe JSON serialization of the whole
         * [git.shin.komorei.model.Listing] (see [ListingArgCodec]) so the
         * screen can open any source listing without re-resolving it.
         */
        fun createRoute(sourceId: String, listing: git.shin.komorei.model.Listing): String {
            return "listing/${Uri.encode(sourceId)}/${ListingArgCodec.encode(listing)}"
        }
    }
}
