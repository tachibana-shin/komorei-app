package git.shin.komorei

import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.ui.screens.sources.RepoSectionState
import git.shin.komorei.ui.screens.sources.buildExternalCatalog
import git.shin.komorei.ui.screens.sources.filterByLanguages
import git.shin.komorei.ui.screens.sources.partitionUpdates
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

    /**
     * A loaded repo section. [name] is what production threads as the row's
     * display repo name ([RepoSectionState.Loaded].name ← manifest repo name);
     * this helper defaults it to [url] so existing tests keep compiling, but
     * attribution tests pass a real name to mirror the sheet ("Kho: KKPhim").
     */
    private fun loaded(
        url: String,
        sources: List<ExternalSourceInfo>,
        name: String = url,
    ): Pair<String, RepoSectionState> = url to RepoSectionState.Loaded(name = name, sources = sources)

    private fun source(
        id: String,
        version: String,
        name: String = id,
        rating: Int = 0,
    ) = ExternalSourceInfo(id = id, name = name, version = version, contentRating = rating)

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
        val catalog =
            buildExternalCatalog(
                repos = listOf(repoA, repoB),
                states =
                    mapOf(
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
        val catalog =
            buildExternalCatalog(
                repos = listOf(repoA, repoB),
                states =
                    mapOf(
                        loaded(repoA, listOf(source("vi.ophim", "1.0.0", name = "OPhim"), source("vi.kkphim", "3.1.0"))),
                        loaded(repoB, listOf(source("vi.ophim", "1.2.0", name = "OPhim")), name = "Kho B"),
                    ),
            )
        val merged = catalog.sources.single { it.id == "vi.ophim" }
        assertEquals("1.2.0", merged.version)
        // The dedication: repoB's manifest advertised the 1.2.0 edition that
        // won the cross-repo dedup, so the row MUST answer "nguồn này thuộc
        // kho nào" — the repo the update/install would actually pull from.
        assertEquals("Kho B", merged.repoName)
        assertTrue(catalog.sources.any { it.id == "vi.kkphim" })
    }

    @Test
    fun `equal versions keep the first repo's copy`() {
        val catalog =
            buildExternalCatalog(
                repos = listOf(repoA, repoB),
                states =
                    mapOf(
                        loaded(repoA, listOf(source("vi.ophim", "1.2.0"))),
                        loaded(repoB, listOf(source("vi.ophim", "1.2.0"))),
                    ),
            )
        assertEquals(1, catalog.sources.size)
    }

    @Test
    fun `loading holds while any repo has not resolved`() {
        val catalog =
            buildExternalCatalog(
                repos = listOf(repoA, repoB),
                states = mapOf(loaded(repoA, listOf(source("a.id", "1.0.0")))),
            )
        assertTrue(catalog.loading)
        assertFalse(catalog.failed)
        assertEquals(1, catalog.sources.size)
    }

    @Test
    fun `failed is set when at least one repo is unavailable`() {
        val catalog =
            buildExternalCatalog(
                repos = listOf(repoA, repoB, repoC),
                states =
                    mapOf(
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
        val catalog =
            listOf(
                ExternalSourceInfo(id = "a", name = "A", version = "1", languages = listOf("vi")),
                ExternalSourceInfo(id = "b", name = "B", version = "1", languages = listOf("multi")),
            )
        assertEquals(2, filterByLanguages(catalog, emptySet()).size)
    }

    @Test
    fun `language filter keeps sources carrying any selected tag`() {
        val catalog =
            listOf(
                ExternalSourceInfo(id = "a", name = "A", version = "1", languages = listOf("vi")),
                ExternalSourceInfo(id = "b", name = "B", version = "1", languages = listOf("multi")),
                ExternalSourceInfo(id = "c", name = "C", version = "1", languages = listOf("vi", "en")),
            )
        assertEquals(listOf("a", "c"), filterByLanguages(catalog, setOf("vi")).map { it.id })
        assertEquals(listOf("a", "b", "c"), filterByLanguages(catalog, setOf("vi", "multi")).map { it.id })
    }

    @Test
    fun `source without language metadata matches multi`() {
        val catalog =
            listOf(
                ExternalSourceInfo(id = "a", name = "A", version = "1"),
                ExternalSourceInfo(id = "b", name = "B", version = "1", languages = listOf("vi")),
            )
        assertEquals(listOf("a"), filterByLanguages(catalog, setOf("multi")).map { it.id })
        assertEquals(listOf("b"), filterByLanguages(catalog, setOf("vi")).map { it.id })
    }

    // ── partitionUpdates ───────────────────────────────────────────────────

    private fun part(
        sources: List<Pair<String, String>>,
        installed: Map<String, String>,
    ) = partitionUpdates(
        sources = sources.map { (id, version) -> ExternalSourceInfo(id = id, name = id, version = version) },
        installedVersions = installed,
    )

    @Test
    fun `partition moves newer advertised versions into updates`() {
        val (updates, current) =
            part(
                listOf("a" to "1.2.0", "b" to "1.0.0", "c" to "0.9.0", "d" to "1.0.0"),
                installed = mapOf("a" to "1.0.0", "b" to "1.0.0", "d" to "1.5.0"),
            )
        assertEquals(listOf("a"), updates.map { it.id })
        assertEquals(listOf("b", "c", "d"), current.map { it.id })
    }

    @Test
    fun `uninstalled sources never count as updates`() {
        val (updates, current) =
            part(
                listOf("fresh" to "9.0.0"),
                installed = emptyMap(),
            )
        assertTrue(updates.isEmpty())
        assertEquals(listOf("fresh"), current.map { it.id })
    }

    @Test
    fun `updates preserve catalog order and only the update ids`() {
        val (updates, current) =
            part(
                listOf("z" to "2.0.0", "a" to "2.0.0", "m" to "1.0.0"),
                installed = mapOf("z" to "1.0.0", "a" to "1.0.0", "m" to "1.0.0"),
            )
        assertEquals(listOf("z", "a"), updates.map { it.id })
        assertEquals(listOf("m"), current.map { it.id })
    }
}
