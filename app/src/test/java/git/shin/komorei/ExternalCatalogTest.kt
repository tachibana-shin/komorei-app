package git.shin.komorei

import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.ui.screens.sources.RepoSectionState
import git.shin.komorei.ui.screens.sources.buildExternalCatalog
import git.shin.komorei.ui.screens.sources.filterByLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the Aidoku-style external-source catalog merge behind the
 * "Thêm nguồn" sheet: repos contribute to ONE deduped list (newest advertised
 * version wins per id), and per-repo fetch state folds into [loading]/[failed].
 */
class ExternalCatalogTest {

    private val repoA = "https://a.example/repos"
    private val repoB = "https://b.example/repos"
    private val repoC = "https://c.example/repos"

    private fun loaded(url: String, sources: List<ExternalSourceInfo>): Pair<String, RepoSectionState> =
        url to RepoSectionState.Loaded(name = url, sources = sources)

    private fun source(id: String, version: String, name: String = id, rating: Int = 0) =
        ExternalSourceInfo(id = id, name = name, version = version, contentRating = rating)

    @Test
    fun `no repos yields an empty not-loading non-failed catalog`() {
        val catalog = buildExternalCatalog(repos = emptyList(), states = emptyMap())
        assertFalse(catalog.hasRepos)
        assertFalse(catalog.loading)
        assertFalse(catalog.failed)
        assertTrue(catalog.sources.isEmpty())
    }

    @Test
    fun `configured repos are flagged even before any state arrives`() {
        val catalog = buildExternalCatalog(repos = listOf(repoA), states = emptyMap())
        assertTrue(catalog.hasRepos)
        assertTrue(catalog.loading)
        assertFalse(catalog.failed)
        assertTrue(catalog.sources.isEmpty())
    }

    @Test
    fun `loaded repos contribute their sources sorted by name`() {
        val catalog = buildExternalCatalog(
            repos = listOf(repoA, repoB),
            states = mapOf(
                loaded(repoA, listOf(source("a.id", "1.0.0", name = "Beta"))),
                loaded(repoB, listOf(source("b.id", "2.0.0", name = "Alpha"))),
            ),
        )
        assertEquals(listOf("Alpha", "Beta"), catalog.sources.map { it.name })
        assertFalse(catalog.loading)
        assertFalse(catalog.failed)
    }

    @Test
    fun `duplicates across repos keep the newest advertised version`() {
        val catalog = buildExternalCatalog(
            repos = listOf(repoA, repoB),
            states = mapOf(
                loaded(repoA, listOf(source("vi.ophim", "1.0.0", name = "OPhim"), source("vi.kkphim", "3.1.0"))),
                loaded(repoB, listOf(source("vi.ophim", "1.2.0", name = "OPhim"))),
            ),
        )
        val merged = catalog.sources.single { it.id == "vi.ophim" }
        assertEquals("1.2.0", merged.version)
        assertTrue(catalog.sources.any { it.id == "vi.kkphim" })
    }

    @Test
    fun `equal versions keep the first repo's copy`() {
        val catalog = buildExternalCatalog(
            repos = listOf(repoA, repoB),
            states = mapOf(
                loaded(repoA, listOf(source("vi.ophim", "1.2.0"))),
                loaded(repoB, listOf(source("vi.ophim", "1.2.0"))),
            ),
        )
        assertEquals(1, catalog.sources.size)
    }

    @Test
    fun `loading holds while any repo has not resolved`() {
        val catalog = buildExternalCatalog(
            repos = listOf(repoA, repoB),
            states = mapOf(loaded(repoA, listOf(source("a.id", "1.0.0")))),
        )
        assertTrue(catalog.loading)
        assertFalse(catalog.failed)
        assertEquals(1, catalog.sources.size)
    }

    @Test
    fun `failed is set when at least one repo is unavailable`() {
        val catalog = buildExternalCatalog(
            repos = listOf(repoA, repoB, repoC),
            states = mapOf(
                loaded(repoA, listOf(source("a.id", "1.0.0"))),
                repoB to RepoSectionState.Unavailable,
                repoC to RepoSectionState.Loading,
            ),
        )
        assertTrue(catalog.failed)
        assertTrue(catalog.loading)
        assertEquals(1, catalog.sources.size)
    }

    @Test
    fun `empty language selection means no filter`() {
        val catalog = listOf(
            ExternalSourceInfo(id = "a", name = "A", version = "1", languages = listOf("vi")),
            ExternalSourceInfo(id = "b", name = "B", version = "1", languages = listOf("multi")),
        )
        assertEquals(2, filterByLanguages(catalog, emptySet()).size)
    }

    @Test
    fun `language filter keeps sources carrying any selected tag`() {
        val catalog = listOf(
            ExternalSourceInfo(id = "a", name = "A", version = "1", languages = listOf("vi")),
            ExternalSourceInfo(id = "b", name = "B", version = "1", languages = listOf("multi")),
            ExternalSourceInfo(id = "c", name = "C", version = "1", languages = listOf("vi", "en")),
        )
        assertEquals(listOf("a", "c"), filterByLanguages(catalog, setOf("vi")).map { it.id })
        assertEquals(listOf("a", "b", "c"), filterByLanguages(catalog, setOf("vi", "multi")).map { it.id })
    }

    @Test
    fun `source without language metadata matches multi`() {
        val catalog = listOf(
            ExternalSourceInfo(id = "a", name = "A", version = "1"),
            ExternalSourceInfo(id = "b", name = "B", version = "1", languages = listOf("vi")),
        )
        assertEquals(listOf("a"), filterByLanguages(catalog, setOf("multi")).map { it.id })
        assertEquals(listOf("b"), filterByLanguages(catalog, setOf("vi")).map { it.id })
    }
}