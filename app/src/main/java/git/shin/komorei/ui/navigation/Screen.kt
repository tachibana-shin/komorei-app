package git.shin.komorei.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Player : Screen("player/{animeId}/{episodeId}?") {
        fun createRoute(animeId: String, episodeId: String? = null): String {
            return if (episodeId != null) "player/$animeId/$episodeId" else "player/$animeId"
        }
    }
    data object Category : Screen("category/{filters}") {
        fun createRoute(filtersJson: String): String {
            return "category/$filtersJson"
        }
    }
}
