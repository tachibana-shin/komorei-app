package git.shin.komorei.sdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import git.shin.komorei.sdk.runner.AnimeStatus
import git.shin.komorei.sdk.runner.DeepLinkResult
import git.shin.komorei.sdk.runner.FilterKind
import git.shin.komorei.sdk.runner.FilterValue
import git.shin.komorei.sdk.runner.HostDefaultValue
import git.shin.komorei.sdk.runner.ListingKind
import git.shin.komorei.sdk.runner.StreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full-stack proof on a REAL device: the real runner (JNA + the app's
 * jniLibs .so) driven by the REAL [KrxHostImpl] — real WebView-backed JS
 * contexts, real CookieManager, real SharedPreferences defaults, real OkHttp —
 * loading the app's own fake source from main assets
 * (`assets/sources/fake-vi-source.krx`).
 *
 * Where the Robolectric tests rely on shadows/`jsEvalOverride`, every layer
 * here is production code on-device: no shadows, no seams. This is the
 * end-to-end "Android app can load and drive a .krx source" proof.
 */
@RunWith(AndroidJUnit4::class)
class FakeViSourceDeviceTest {
    private lateinit var host: KrxHostImpl
    private lateinit var runner: git.shin.komorei.sdk.runner.KomoreiRunner

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val krxBytes = context.assets.open("sources/fake-vi-source.krx").use { it.readBytes() }
        assertTrue("krx must be non-empty", krxBytes.isNotEmpty())
        host = KrxHostImpl(context)
        runner = KrxManager.load(host, krxBytes)
    }

    @Test
    fun search_returns_paginated_catalog() {
        val page1 = runner.search(null, 1, emptyList())
        assertEquals(15, page1.entries.size)
        assertTrue(page1.hasNextPage)
        val first = page1.entries.first()
        assertEquals("solo_leveling_s2", first.key)
        assertEquals("vi.fake-source", first.sourceId)
        assertEquals("Solo Leveling: Arise from the Shadow", first.title)
        assertEquals(AnimeStatus.ONGOING, first.status)
    }

    @Test
    fun query_and_filters_work() {
        val byQuery = runner.search("Frieren", 1, emptyList()).entries
        assertEquals(1, byQuery.size)
        assertEquals("frieren_journey", byQuery.single().key)

        val action =
            runner
                .search(
                    null,
                    1,
                    listOf(FilterValue.MultiSelect("genres", listOf("Hành Động"), emptyList())),
                ).entries
        assertTrue(action.isNotEmpty())
        assertTrue(action.all { it.genres.any { g -> g.name == "Hành Động" } })
    }

    @Test
    fun anime_update_upgrades_and_streams_resolve() {
        val lite = runner.search("Frieren", 1, emptyList()).entries.single()
        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = true)
        assertTrue(full.description!!.isNotEmpty())
        assertEquals(28, full.episodes!!.size)
        assertEquals("Madhouse", full.studio?.name)

        val episode = full.episodes!!.first()
        val streams = runner.streamList(full, episode)
        assertEquals(3, streams.size)
        val hls = runner.stream(full, episode, streams[0])
        assertEquals(StreamType.HLS, hls.streamType)
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", hls.url)
        assertTrue(hls.isContent)
        assertEquals(1, hls.subtitles.size)
        assertEquals("vi", hls.subtitles[0].language)
    }

    @Test
    fun defaults_read_write_flip_server_order() {
        val lite = runner.search("Frieren", 1, emptyList()).entries.single()
        val full = runner.animeUpdate(lite, true, true)
        val episode = full.episodes!!.first()

        val before = runner.streamList(full, episode).map { it.key }
        assertEquals(listOf("hls", "mp4_720", "mp4_fhd"), before)

        // write through the real (CookieManager-adjacent) defaults store
        host.defaultsSet("prefer_fhd", HostDefaultValue.Bool(true))
        val after = runner.streamList(full, episode).map { it.key }
        assertEquals(listOf("mp4_fhd", "mp4_720", "hls"), after)

        host.defaultsSet("prefer_fhd", HostDefaultValue.Bool(false))
    }

    @Test
    fun filters_settings_listings_home_all_exposed() {
        val filters = runner.filters()
        assertTrue(filters.any { it.id == "genres" && (it.kind as FilterKind.MultiSelect).isGenre })
        assertTrue(filters.any { it.id == "year" && (it.kind as FilterKind.Range).max == 2025.0f })

        val settings = runner.settings()
        assertTrue(settings.any { it.key == "prefer_fhd" })

        val listings = runner.listings()
        assertEquals(listOf("latest", "popular", "ongoing", "completed"), listings.map { it.id })
        assertTrue(listings.all { it.kind == ListingKind.LIST })

        val home = runner.home()
        assertEquals(4, home.components.size)
        assertTrue(home.components[0].value is git.shin.komorei.sdk.runner.HomeComponentValue.BigScroller)
    }

    @Test
    fun deep_link_resolves() {
        val anime = runner.deepLink("https://komorei.example/anime/frieren_journey")
        assertNotNull(anime)
        assertTrue(anime is DeepLinkResult.Anime)
        assertEquals("frieren_journey", (anime as DeepLinkResult.Anime).key)

        assertNull(runner.deepLink("https://komorei.example/nope"))
    }
}
