package git.shin.komorei

import git.shin.komorei.data.backup.BackupAnime
import git.shin.komorei.data.backup.BackupCodec
import git.shin.komorei.data.backup.BackupFormatException
import git.shin.komorei.data.backup.BackupPayload
import git.shin.komorei.data.backup.BackupSourceDefault
import git.shin.komorei.data.backup.BackupSourceState
import git.shin.komorei.data.backup.BackupUserSource
import git.shin.komorei.data.backup.BackupWatchHistory
import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.CategoryLink
import git.shin.komorei.model.FilterValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec()

    @Test
    fun roundTripsPolymorphicAnimeFiltersAndAllSections() {
        val anime =
            Anime(
                id = "anime-1",
                sourceId = "source-1",
                title = "Anime",
                originalTitle = "Anime",
                posterUrl = "",
                bannerUrl = "",
                description = "Description",
                episodeCount = 12,
                currentEpisode = null,
                rating = 8.5f,
                ratingCount = 10,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2026"),
                genres = listOf(CategoryLink("Action", listOf(FilterValue.Text("q", "demo")))),
                authors = emptyList(),
                studio = null,
                seasonOf = null,
            )
        val payload =
            BackupPayload(
                createdAt = 123L,
                appVersion = "1.0",
                appBuild = 1,
                name = "snapshot",
                anime = listOf(BackupAnime(anime, true, 100L)),
                watchHistory =
                    listOf(
                        BackupWatchHistory("anime-1", "source-1", "episode-1", "1", "Ep 1", 200L, 3_000L, 24_000L),
                    ),
                sourceDefaults = listOf(BackupSourceDefault("source-1.language", "string", "vi")),
                sourceState =
                    BackupSourceState(
                        disabledSources = listOf("old"),
                        pinnedSources = listOf("source-1"),
                        repositoryUrls = listOf("https://example.test/repo.json"),
                    ),
                searchHistory = listOf("one", "two"),
                userSources = listOf(BackupUserSource("source-1", "AQID")),
            )

        val restored = codec.decode(codec.encode(payload))

        assertEquals(payload, restored)
        assertEquals(
            FilterValue.Text("q", "demo"),
            restored.anime!!
                .single()
                .anime.genres
                .single()
                .filters
                .single(),
        )
    }

    @Test
    fun preservesNullSections() {
        val payload =
            BackupPayload(
                createdAt = 123L,
                appVersion = "1.0",
                appBuild = 1,
                anime = emptyList(),
            )

        val restored = codec.decode(codec.encode(payload))

        assertTrue(restored.anime != null)
        assertTrue(restored.watchHistory == null)
        assertTrue(restored.sourceDefaults == null)
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsFutureSchema() {
        codec.decode(
            """{"format":"komorei.backup","schemaVersion":99,"createdAt":1,"appVersion":"1","appBuild":1}""",
        )
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsMalformedJson() {
        codec.decode("not-json")
    }
}
