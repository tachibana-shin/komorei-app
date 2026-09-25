package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.deeplink.DeepLinkResolver
import git.shin.komorei.model.DeepLinkTarget
import git.shin.komorei.sdk.KrxHostImpl
import git.shin.komorei.sdk.KrxSourceRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * DeepLinkHandler plumbing — the "find the source that claims this URL" half.
 * Runs on the REAL runner + [KrxHostImpl] + the committed `fake-vi-source.krx`
 * fixture: `handle_deep_link` walks `/anime/<key>` → `/watch/<anime>/<ep>` →
 * `/list/<id>` and returns `None` for anything else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeepLinkResolverTest {
    private lateinit var resolver: DeepLinkResolver

    companion object {
        private val fakeKrx: String =
            System.getProperty("komorei.test.fakeKrx")
                ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() =
        runBlocking {
            assertNotNull(
                "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
                System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
            )
            val context = ApplicationProvider.getApplicationContext<Context>()
            val registry = KrxSourceRegistry(context, KrxHostImpl(context))
            val runner = registry.loadKrx("vi.fake-source", File(fakeKrx).readBytes())
            assertNotNull("fake source should load", runner)
            resolver = DeepLinkResolver(AnimeRepository(registry))
        }

    @Test
    fun animeLinkResolvesToAnimeTarget() =
        runBlocking {
            val hit = resolver.resolve("https://komorei.example/anime/frieren_journey")

            assertNotNull(hit)
            assertEquals("vi.fake-source", hit!!.sourceId)
            assertEquals(DeepLinkTarget.Anime("frieren_journey"), hit.target)
        }

    @Test
    fun watchLinkResolvesToEpisodeTargetWithRawKeys() =
        runBlocking {
            val hit = resolver.resolve("komorei://komorei.example/watch/frieren_journey/frieren_journey_ep_2")

            assertNotNull(hit)
            assertEquals("vi.fake-source", hit!!.sourceId)
            assertEquals(
                DeepLinkTarget.Episode("frieren_journey", "frieren_journey_ep_2"),
                hit.target,
            )
        }

    @Test
    fun listingLinkResolvesToListingTarget() =
        runBlocking {
            val hit = resolver.resolve("https://komorei.example/list/popular")

            assertNotNull(hit)
            assertEquals("vi.fake-source", hit!!.sourceId)
            val listing = (hit.target as DeepLinkTarget.Listing).listing
            assertEquals("popular", listing.id)
            assertEquals("popular", listing.name)
        }

    @Test
    fun unknownUrlResolvesToNull() =
        runBlocking {
            // No /anime/, /watch/ or /list/ segment → the fake returns None.
            assertNull(resolver.resolve("https://komorei.example/bogus-page"))
        }

    @Test
    fun hostMismatchStillTriesEverySource() =
        runBlocking {
            // A source may own links on other domains (domain-wide matching is a
            // HINT for ordering only — the source still decides via handle_deep_link).
            val hit = resolver.resolve("https://cdn.other.example/anime/one_piece")

            assertNotNull(hit)
            assertEquals("vi.fake-source", hit!!.sourceId)
            assertEquals(DeepLinkTarget.Anime("one_piece"), hit.target)
        }
}
