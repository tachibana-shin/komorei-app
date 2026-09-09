package git.shin.komorei

import git.shin.komorei.data.AnimeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeRepositoryTest {

    private val repository = AnimeRepository()

    @Test
    fun testSourcesList() {
        val sources = repository.sources
        assertTrue("Sources should not be empty", sources.isNotEmpty())
        assertTrue("Should contain AnimeVietsub source", sources.any { it.id == "animevietsub" })
    }

    @Test
    fun testFeaturedAnime() {
        val featured = repository.getFeaturedAnime("all")
        assertTrue("Featured anime should have items", featured.isNotEmpty())
        val first = featured.first()
        assertNotNull(first.title)
        assertTrue(first.rating > 0.0)
    }

    @Test
    fun testSectionsForSource() {
        val sections = repository.getSectionsForSource("animevietsub")
        assertTrue("Sections map should not be empty", sections.isNotEmpty())
        assertTrue(sections.containsKey("Mới Cập Nhật"))
    }

    @Test
    fun testMultiSourceSearch() = runBlocking {
        val results = repository.searchMultiSource(query = "Solo", selectedGenreId = null)
        assertTrue("Multi-source search should find Solo Leveling", results.isNotEmpty())
        val totalAnimes = results.values.flatten()
        assertTrue(totalAnimes.any { it.title.contains("Solo", ignoreCase = true) })
    }
}
