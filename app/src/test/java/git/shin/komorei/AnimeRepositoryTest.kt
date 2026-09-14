package git.shin.komorei

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.HomeComponentValue
import git.shin.komorei.model.ListingKind
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The runner-backed [AnimeRepository], driven through the REAL runner (uniffi
 * bindings + linux cdylib) + REAL [KrxHostImpl] + the committed
 * `fake-vi-source.krx` fixture. Same harness as
 * [git.shin.komorei.sdk.FakeViSourceRunnerIntegrationTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnimeRepositoryTest {

    private lateinit var repository: AnimeRepository

    companion object {
        private val fakeKrx: String = System.getProperty("komorei.test.fakeKrx")
            ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() = runBlocking {
        assertNotNull(
            "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
            System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val registry = KrxSourceRegistry(context, KrxHostImpl(context))
        // Load the fake source directly (assets-based discovery is also exercised,
        // but loading by bytes makes the test hermetic).
        val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
        assertNotNull("fake source should load", runner)
        repository = AnimeRepository(registry)
    }

    @Test
    fun testSourcesList() {
        val sources = repository.sources
        assertTrue("Sources should not be empty", sources.isNotEmpty())
        assertTrue("Should contain aggregator", sources.any { it.id == "all" })
        assertTrue("Should contain fake source", sources.any { it.id == "vi.fake-source" })
    }

    @Test
    fun testFeaturedAnime() = runBlocking {
        val featured = repository.getFeaturedAnime("vi.fake-source")
        assertTrue("Featured anime should have items", featured.isNotEmpty())
        val first = featured.first()
        assertNotNull(first.title)
        assertTrue((first.rating ?: 0f) > 0.0f)
    }

    @Test
    fun testSectionsForSource() = runBlocking {
        val sections = repository.getSectionsForSource("vi.fake-source")
        assertTrue("Sections map should not be empty", sections.isNotEmpty())
    }

    @Test
    fun testGetHomeFullComponents() = runBlocking {
        val home = repository.getHome("vi.fake-source")

        // The fake source's get_home returns ALL 7 lossless rows, in order —
        // one per HomeComponentValue variant.
        assertEquals(
            listOf("Khám Phá", "Nổi Bật", "Đang Hot", "Mới Cập Nhật", "Phổ Biến Nhất", "Thể Loại", "Liên Kết"),
            home.map { it.title },
        )

        // 1. ImageScroller — banner links with images
        val promo = home[0].value as HomeComponentValue.ImageScroller
        assertTrue(promo.links.isNotEmpty())
        assertTrue(promo.links.all { it.imageUrl != null })
        assertEquals(4.0f, promo.autoScrollInterval ?: 0f, 0.01f)

        // 2. BigScroller — featured hero
        val big = home[1].value as HomeComponentValue.BigScroller
        assertTrue("BigScroller drives featured", big.entries.isNotEmpty())
        assertEquals(5.0f, big.autoScrollInterval ?: 0f, 0.01f)

        // 3. Scroller — small rail with a listing (links carry poster covers)
        val hot = home[2].value as HomeComponentValue.Scroller
        assertEquals("hot", hot.listing?.id)
        assertTrue(hot.entries.isNotEmpty())
        assertTrue(hot.entries.all { it.anime != null })
        assertTrue("Scroller links carry poster covers", hot.entries.all { it.imageUrl != null })
        assertTrue("Scroller lite anime carry the quality label", hot.entries.all { it.anime?.qualityTag != null })

        // 4. AnimeEpisodeList — recent updates (the app's "Mới Cập Nhật").
        val updates = home[3].value as HomeComponentValue.AnimeEpisodeList
        assertEquals("latest", updates.listing?.id)
        assertEquals(ListingKind.LIST, updates.listing?.kind)
        assertTrue(updates.entries.all { it.episode.animeId == it.anime.id })
        assertTrue("episode carries the upload timestamp", updates.entries.all { it.episode.dateUploaded != null })

        // 5. AnimeList — popular ranking (links carry poster covers)
        val popular = home[4].value as HomeComponentValue.AnimeList
        assertEquals("popular", popular.listing?.id)
        assertTrue("AnimeList ranking preserved", popular.ranking)
        assertEquals(6, popular.pageSize)
        assertTrue(popular.entries.all { it.anime != null })
        assertTrue("AnimeList links carry poster covers", popular.entries.all { it.imageUrl != null })

        // 6. Filters — genre chips with actionable MultiSelect values
        val genres = home[5].value as HomeComponentValue.Filters
        assertEquals(12, genres.items.size)
        // One chip per genre, Vietnamese titles matching the app's Genre list,
        // each carrying an actionable MultiSelect value for the "genres" filter.
        assertEquals("Hành Động", genres.items.first().title)
        assertTrue(genres.items.all { it.values != null })
        val chip = genres.items.first().values!!.first() as FilterValue.MultiSelect
        assertEquals("genres", chip.id)
        assertEquals(listOf("Hành Động"), chip.included)

        // 7. Links — plain navigation shortcuts
        val links = home[6].value as HomeComponentValue.Links
        assertEquals(4, links.links.size)
        assertTrue(links.links.all { it.title.isNotBlank() })

        // Derived helpers stay consistent with the full layout.
        assertEquals(
            big.entries.map { it.id },
            repository.getFeaturedAnime("vi.fake-source").map { it.id },
        )
        // The anime-bearing rails — Scroller "Đang Hot" is included too.
        assertEquals(
            setOf("Đang Hot", "Mới Cập Nhật", "Phổ Biến Nhất"),
            repository.getSectionsForSource("vi.fake-source").keys,
        )
    }

    @Test
    fun testGetFiltersAndPerSourceSearch() = runBlocking {
        val filters = repository.getFilters("vi.fake-source")
        val genre = filters.first {
            val k = it.kind
            k is FilterKind.MultiSelect && k.isGenre
        }
        assertEquals("genres", genre.id)

        val page = repository.search(
            sourceId = "vi.fake-source",
            query = null,
            page = 1,
            selected = listOf(FilterValue.MultiSelect("genres", listOf("Hành Động"), emptyList())),
        )
        assertTrue("Genre search should return results", page.entries.isNotEmpty())
        assertTrue(page.entries.all { anime -> anime.genres.any { it.name == "Hành Động" } })
    }

    @Test
    fun testMultiSourceSearch() = runBlocking {
        val results = repository.searchMultiSource(query = "Solo", selectedGenreId = null)
        assertTrue("Multi-source search should find Solo Leveling", results.isNotEmpty())
        val totalAnimes = results.values.flatten()
        assertTrue(totalAnimes.any { it.title.contains("Solo", ignoreCase = true) })
    }

    @Test
    fun testSearchByGenre() = runBlocking {
        // "Hành Động" maps through the app Genre id "action" → the source filters.
        val results = repository.searchMultiSource(query = "", selectedGenreId = "action")
        assertTrue(results.isNotEmpty())
        val all = results.values.flatten()
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { anime -> anime.genres.any { it.name == "Hành Động" } })
    }

    @Test
    fun testEveryGenreSearchable() = runBlocking {
        // get_home carries one chip per genre the app exposes, in the same
        // order as the app's Genre list — the home "Thể Loại" row is complete.
        val chipTitles =
            (repository.getHome("vi.fake-source")[5].value as HomeComponentValue.Filters)
                .items.map { it.title }
        assertEquals(repository.genres.map { it.name }, chipTitles)

        // ...and the catalog is tagged so EVERY genre id returns results.
        for (genre in repository.genres) {
            val results = repository.searchMultiSource(query = "", selectedGenreId = genre.id)
            assertTrue(
                "Genre '${genre.name}' (${genre.id}) should return results",
                results.isNotEmpty(),
            )
            val all = results.values.flatten()
            assertTrue(
                "All results for '${genre.name}' must carry the genre tag",
                all.isNotEmpty() && all.all { anime -> anime.genres.any { it.name == genre.name } },
            )
        }
    }

    @Test
    fun testFindAnimeById() = runBlocking {
        val anime = repository.findAnimeById("frieren_journey")
        assertNotNull("Should locate Frieren by id", anime)
        assertEquals("frieren_journey", anime?.id)
        assertEquals("vi.fake-source", anime?.sourceId)
    }

    @Test
    fun testAnimeUpdateUpgradesLiteToFull() = runBlocking {
        val lite = repository.findAnimeById("solo_leveling_s2") ?: return@runBlocking
        val full = repository.getAnimeUpdate(lite, needsDetails = true, needsChapters = true)
        assertTrue("Full update should bring episodes", full.episodes.isNotEmpty())
        assertTrue("Rating present after upgrade", (full.rating ?: 0f) > 0.0f)
    }
}