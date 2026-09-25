package git.shin.komorei

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.CategoryLink
import git.shin.komorei.model.FilterValue
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The "Có thể bạn sẽ thích" recommendation fallback: [AnimeRepository
 * .getRecommendedAnime] asks the source for its own `get_recommended_anime`
 * selection (the SDK's optional `RecommendationsHandler` trait) and, when the
 * source does NOT implement it (`RunnerException.ExportMissing`), falls back to
 * a search by the anime's FIRST genre tag.
 *
 * Driven through the REAL runner (uniffi bindings + linux cdylib) + REAL
 * [KrxHostImpl] + the SDK's own `example-source` (which has no
 * RecommendationsHandler): its search is fully offline and deterministic, so
 * the whole fallback path is exercised hermetically.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecommendedAnimeFallbackTest {
    private lateinit var repository: AnimeRepository

    companion object {
        private val exampleKrx: String =
            System.getProperty("komorei.test.exampleKrx")
                ?: error("missing -Dkomorei.test.exampleKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() =
        runBlocking {
            assertNotNull(
                "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
                System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
            )
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val registry = KrxSourceRegistry(context, KrxHostImpl(context))
            val runner = registry.loadKrx("en.example-source", File(exampleKrx).readBytes())
            assertNotNull("example source should load", runner)
            repository = AnimeRepository(registry)
        }

    @Test
    fun `recommended anime falls back to a search by the first genres own filters`() =
        runBlocking {
            // The example source does NOT implement RecommendationsHandler. Give the
            // anime a first genre that CARRIES filters — the fallback must search
            // with exactly those (no genre-filter lookup needed). example-source
            // search ignores filters, so the full "Anime N" catalog page returns.
            val lite = repository.search("en.example-source", null, 1).entries.first()
            val withTag =
                lite.copy(
                    genres =
                        listOf(
                            CategoryLink(
                                name = "Action",
                                filters = listOf(FilterValue.MultiSelect("genres", listOf("Action"), emptyList())),
                            ),
                        ),
                )

            val page = repository.getRecommendedAnime(withTag)
            assertTrue("first-genre tag search should fill the section", page.entries.isNotEmpty())
            assertTrue("the current anime is never suggested", page.entries.none { it.id == withTag.id })
        }

    @Test
    fun `recommended anime degrades to an empty page when no genre tag is usable`() =
        runBlocking {
            // Real example-source entries carry a "Action" genre with NO filters and
            // the source ships no genre filter (`get_filters` has no isGenre) — so
            // neither the genre's own values nor buildGenreFilter produce a filter,
            // and the fallback returns an empty page WITHOUT throwing.
            val lite = repository.search("en.example-source", null, 1).entries.first()
            assertEquals("Action", lite.genres.firstOrNull()?.name)
            assertTrue(lite.genres.all { it.filters.isEmpty() })

            val page = repository.getRecommendedAnime(lite)
            assertTrue(page.entries.isEmpty())
            assertFalse(page.hasNextPage)
        }
}
