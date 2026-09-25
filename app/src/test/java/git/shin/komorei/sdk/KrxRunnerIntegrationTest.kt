package git.shin.komorei.sdk

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.runner.AnimeStatus
import git.shin.komorei.sdk.runner.DeepLinkResult
import git.shin.komorei.sdk.runner.FilterKind
import git.shin.komorei.sdk.runner.HostDefaultValue
import git.shin.komorei.sdk.runner.Listing
import git.shin.komorei.sdk.runner.ListingKind
import git.shin.komorei.sdk.runner.RunnerException
import git.shin.komorei.sdk.runner.SettingValue
import git.shin.komorei.sdk.runner.StreamType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * End-to-end: the real runner (uniffi bindings + linux cdylib) driven by the
 * real Kotlin host ([KrxHostImpl] — Jsoup + OkHttp + SharedPreferences) against
 * the reference source (`komorei-sdk/examples/example-source/package.krx`).
 *
 * This mirrors the Rust contract tests (`komorei-sdk/crates/runner/tests/host.rs`)
 * through the actual JNA ABI on the JVM: search → animeUpdate → streams →
 * filters → settings → listings → home → deep link → missing exports, plus
 * the [KrxMappings] conversions to the app model layer. The only exceptions to
 * the canned host are IO: [KrxHostImpl.netRequest] performs a real fetch of
 * `https://example.com` during `animeUpdate`.
 *
 * The linux cdylib is pointed at via
 * `-Duniffi.component.komorei_runner.libraryOverride` (set in the companion
 * init before JNA registers the library). Rebuild it with:
 * `cargo build -p komorei-runner` inside `komorei-sdk/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KrxRunnerIntegrationTest {
    private lateinit var host: KrxHostImpl
    private lateinit var runner: git.shin.komorei.sdk.runner.KomoreiRunner

    companion object {
        /**
         * Injected by app/build.gradle.kts (testOptions.unitTests.all) — the
         * uniffi bindings read this to locate the cdylib. Note: File.exists is
         * unreliable inside the Robolectric sandbox classloader, so the JVM args
         * (not file probing) are the source of truth for both paths.
         */
        private val exampleKrx: String =
            System.getProperty("komorei.test.exampleKrx")
                ?: error("missing -Dkomorei.test.exampleKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() {
        val override = System.getProperty("uniffi.component.komorei_runner.libraryOverride")
        assertNotNull(
            "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
            override,
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        host = KrxHostImpl(context)
        runner = KrxManager.load(host, File(exampleKrx).readBytes())
    }

    @Test
    fun `search returns the reference lite entries without io`() {
        val page = runner.search(null, 1, emptyList())
        assertEquals(20, page.entries.size)
        assertTrue(page.hasNextPage)

        val first = page.entries.first()
        assertEquals("1", first.key)
        assertEquals("en.example-source", first.sourceId)
        assertEquals("Anime 1", first.title)
        assertEquals("https://example.com/cover.png", first.cover)
        assertEquals(AnimeStatus.ONGOING, first.status)
        assertEquals("Tập 12/12", first.currentEpisode)
        assertEquals(12, first.episodeCount)
        assertEquals(1, first.genres.size)
        assertEquals("Action", first.genres[0].name)
    }

    @Test
    fun `anime update upgrades lite to full with details and chapters`() {
        val lite = runner.search(null, 1, emptyList()).entries.first()
        val full = runner.animeUpdate(lite, true, true)

        assertEquals("Example Domain", full.description)
        assertEquals("https://example.com/cover.png", full.banner)
        assertEquals(AnimeStatus.ONGOING, full.status)
        assertEquals("2024", full.releaseYear!!.name)
        assertEquals("Author", full.authors[0].name)
        assertEquals("Studio", full.studio!!.name)
        assertEquals(8.5f, full.rating!!, 0.0f)
        assertEquals(1234, full.ratingCount!!)
        assertEquals(99999, full.views)
        assertEquals("Tập 13 phát sóng 20:00 thứ 7", full.nextEpisodeAirInfo)
        assertEquals("FHD", full.qualityTag)
        assertEquals("https://example.com/anime/1", full.url)
        assertEquals(2, full.seasons.size)
        assertEquals("1", full.seasons[0].id)
        assertEquals("Season 2", full.seasons[1].title)

        val episodes = full.episodes!!
        assertEquals(8, episodes.size)
        assertEquals("8", episodes[0].key)
        assertEquals("7", episodes[1].key)
        assertEquals("Title", episodes[1].title)
        assertEquals("https://example.com/cover.png", episodes[1].thumbnail)
        assertEquals("1080p FHD", episodes[1].quality)
        assertEquals(1692318525L, episodes[2].dateUploaded)
        assertEquals("1", episodes[7].key)
    }

    @Test
    fun `stream list resolves two servers and their data`() {
        val lite = runner.search(null, 1, emptyList()).entries.first()
        val full = runner.animeUpdate(lite, true, true)
        val episode = full.episodes!!.last() // key "1" — mirrors the Rust sample episode

        val streams = runner.streamList(full, episode)
        assertEquals(2, streams.size)
        assertEquals("mux", streams[0].key)
        assertEquals("Server 1", streams[0].name)
        assertEquals("1080p", streams[0].quality)
        assertEquals("mp4", streams[1].key)
        assertEquals("Server 2", streams[1].name)
        assertEquals("720p", streams[1].quality)

        val hls = runner.stream(full, episode, streams[0])
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", hls.url)
        assertEquals(StreamType.HLS, hls.streamType)
        assertTrue(hls.isContent)
        assertEquals("Komorei/1.0", hls.headers["User-Agent"])
        assertEquals(1, hls.subtitles.size)
        assertEquals("https://example.com/subs/vi.vtt", hls.subtitles[0].url)
        assertEquals("vi", hls.subtitles[0].language)
        assertEquals("Tiếng Việt", hls.subtitles[0].label)
        assertEquals(0L, hls.intro!!.startMs)
        assertEquals(90_000L, hls.intro!!.endMs)
        assertEquals(1_500_000L, hls.outro!!.startMs)
        assertEquals(1_590_000L, hls.outro!!.endMs)

        val mp4 = runner.stream(full, episode, streams[1])
        assertEquals(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            mp4.url,
        )
        assertEquals(StreamType.MP4, mp4.streamType)
        assertTrue(mp4.isContent)
        assertEquals("Komorei/1.0", mp4.headers["User-Agent"])
        assertTrue(mp4.subtitles.isEmpty())
        assertNull(mp4.intro)
        assertNull(mp4.outro)
    }

    @Test
    fun `dynamic filters decode to the seven kinds`() {
        val filters = runner.filters()
        assertEquals(7, filters.size)
        val byId = filters.associateBy { it.id }

        val text = byId.getValue("text").kind as FilterKind.Text
        assertEquals("Search", text.placeholder)

        val sort = byId.getValue("sort").kind as FilterKind.Sort
        assertTrue(sort.canAscend)
        assertEquals(listOf("Popular", "Recent"), sort.options)
        assertNull(sort.default)

        assertTrue((byId.getValue("check").kind as FilterKind.Check).canExclude)

        val select = byId.getValue("select").kind as FilterKind.Select
        assertTrue(select.usesTagStyle)
        assertEquals(listOf("One", "Two"), select.options)

        val multi = byId.getValue("mselect").kind as FilterKind.MultiSelect
        assertTrue(multi.canExclude)
        assertFalse(multi.usesTagStyle)
        assertEquals(listOf("One", "Two"), multi.options)

        assertEquals("Testing note", (byId.getValue("note").kind as FilterKind.Note).v1)

        val range = byId.getValue("range").kind as FilterKind.Range
        assertEquals(0.0f, range.min!!, 0.0f)
        assertEquals(100.0f, range.max!!, 0.0f)
        assertTrue(range.decimal)
    }

    @Test
    fun `settings reflect host defaults`() {
        val settings = runner.settings()
        assertEquals(1, settings.size)
        assertEquals("setting", settings[0].key)
        assertEquals("Toggle", settings[0].title)
        assertEquals("test", settings[0].notification)
        assertEquals(listOf("settings"), settings[0].refreshes)
        assertTrue(settings[0].value is SettingValue.Toggle)
        assertFalse((settings[0].value as SettingValue.Toggle).default)

        host.defaultsSet("setting", HostDefaultValue.Bool(true))
        val after = runner.settings()
        assertEquals(2, after.size)
        assertEquals("setting2", after[1].key)
        assertEquals("Toggle 2", after[1].title)
    }

    @Test
    fun `listings and anime list dispatch`() {
        val listings = runner.listings()
        assertEquals(1, listings.size)
        assertEquals("listing", listings[0].id)
        assertEquals("Listing", listings[0].name)
        assertEquals(ListingKind.LIST, listings[0].kind)

        val page = runner.animeList(listings[0], 1)
        assertEquals(1, page.entries.size)
        assertEquals("1", page.entries[0].key)
        assertEquals("Anime 1", page.entries[0].title)
        assertFalse(page.hasNextPage)

        val err =
            runCatching { runner.animeList(Listing("test", "Test", ListingKind.DEFAULT), 1) }
                .exceptionOrNull()
        assertTrue("expected Source error, got $err", err is RunnerException.Source)
        assertEquals(-1, (err as RunnerException.Source).code)
        assertTrue("message was: ${err.message}", err.message!!.contains("Not supported"))
    }

    @Test
    fun `home exposes the seven components`() {
        val home = runner.home()
        val titles = home.components.map { it.title.orEmpty() }
        assertEquals(
            listOf(
                "Big Scroller",
                "Anime Episode List",
                "Anime List",
                "Anime List (Paged, Ranking)",
                "Scroller",
                "Filters",
                "Links",
            ),
            titles,
        )
    }

    @Test
    fun `deep link resolves an anime`() {
        val result = runner.deepLink("https://example.com/anime/1")
        assertNotNull(result)
        assertTrue("got $result", result is DeepLinkResult.Anime)
        assertEquals("anime_key", (result as DeepLinkResult.Anime).key)
    }

    @Test
    fun `missing base url export surfaces ExportMissing`() {
        val err = runCatching { runner.baseUrl() }.exceptionOrNull()
        assertTrue("expected ExportMissing, got $err", err is RunnerException.ExportMissing)
        assertEquals("get_base_url", (err as RunnerException.ExportMissing).name)
    }

    @Test
    fun `segment interceptors default to identity`() {
        val url = "https://example.com/media/seg-1.ts"
        assertEquals(url, runner.interceptSegmentUrl(null, url))
        val data = byteArrayOf(0x47, 0x40, 0x00, 0x10)
        assertArrayEquals(data, runner.interceptSegmentData(null, url, data))
    }

    @Test
    fun `mappings convert sdk records to app models`() {
        val lite =
            runner
                .search(null, 1, emptyList())
                .entries
                .first()
                .toAppModel()
        assertEquals("1", lite.id)
        assertEquals("Anime 1", lite.title)
        assertEquals("https://example.com/cover.png", lite.posterUrl)
        assertEquals(12, lite.episodeCount)

        val bindingFull = runner.animeUpdate(runner.search(null, 1, emptyList()).entries.first(), true, true)
        val full = bindingFull.toAppModel()
        assertEquals(8, full.episodes.size)
        assertEquals(2, full.seasons.size)
        assertEquals("Example Domain", full.description)
        assertEquals("2024", full.releaseYear?.name)
        assertEquals("FHD", full.qualityTag)
        assertEquals(git.shin.komorei.model.AnimeStatus.ONGOING, full.status)

        val episode = full.episodes.last()
        assertEquals("1", episode.id)
        assertEquals("1", episode.episodeNumber)

        // stream mapping
        val bindingEpisode = bindingFull.episodes!!.last()
        val stream =
            runner
                .stream(bindingFull, bindingEpisode, runner.streamList(bindingFull, bindingEpisode)[0])
                .toAppModel()
        assertEquals(git.shin.komorei.model.StreamType.HLS, stream.type)
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", stream.url)
        assertTrue(stream.isContent)
        assertEquals(1, stream.subtitles.size)
        assertEquals(0L, stream.intro?.startMs)

        // filter mapping
        val note = runner.filters().first { it.id == "note" }.toAppModel()
        assertTrue(note.kind is git.shin.komorei.model.FilterKind.Note)
    }
}
