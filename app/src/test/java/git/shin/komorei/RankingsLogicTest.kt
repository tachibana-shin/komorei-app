package git.shin.komorei

import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.ui.screens.rankings.RankingPeriod
import git.shin.komorei.ui.screens.rankings.formatViews
import git.shin.komorei.ui.screens.rankings.rankAnimes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rankings are derived purely from the local catalogue — no per-period backend
 * yet — so the ordering keys and the top-N cap deserve a lock.
 */
class RankingsLogicTest {

    private fun anime(
        id: String,
        views: Int = 0,
        rating: Float? = null,
        episodeCount: Int = 0,
    ) = Anime(
        id = id,
        sourceId = "fake",
        title = "Anime $id",
        originalTitle = "",
        posterUrl = "",
        bannerUrl = "",
        description = "",
        episodeCount = episodeCount,
        currentEpisode = null,
        rating = rating,
        ratingCount = null,
        status = AnimeStatus.UNKNOWN,
        releaseYear = null,
        genres = emptyList(),
        authors = emptyList(),
        studio = null,
        seasonOf = null,
        views = views,
    )

    @Test
    fun `day ranks by views descending`() {
        val ranked = rankAnimes(
            listOf(anime("a", views = 10), anime("b", views = 900), anime("c", views = 100)),
            RankingPeriod.DAY,
        )
        assertEquals(listOf("b", "c", "a"), ranked.map { it.anime.id })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
    }

    @Test
    fun `week ranks by rating before views`() {
        val ranked = rankAnimes(
            listOf(
                anime("a", views = 9_000, rating = 7.0f),
                anime("b", views = 1, rating = 9.5f),
            ),
            RankingPeriod.WEEK,
        )
        assertEquals(listOf("b", "a"), ranked.map { it.anime.id })
    }

    @Test
    fun `month ranks by episode count before views`() {
        val ranked = rankAnimes(
            listOf(
                anime("a", views = 9_000, episodeCount = 5),
                anime("b", views = 1, episodeCount = 100),
            ),
            RankingPeriod.MONTH,
        )
        assertEquals(listOf("b", "a"), ranked.map { it.anime.id })
    }

    @Test
    fun `formatViews compacts thousands and millions`() {
        assertEquals("999", formatViews(999))
        assertEquals("1.2K", formatViews(1_234))
        assertEquals("1.2M", formatViews(1_234_567))
    }
}
