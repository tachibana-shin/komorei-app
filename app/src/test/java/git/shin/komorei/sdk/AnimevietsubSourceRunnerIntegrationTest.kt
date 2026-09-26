package git.shin.komorei.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.runner.KomoreiRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import git.shin.komorei.sdk.runner.Anime as RunnerAnime

/**
 * The REAL AnimeVietsub source (`sources/sources/vi.animevietsub` → `package.krx`)
 * through the real runner and the real Kotlin host, with the network side a
 * LOCAL server replaying the site's own captured pages.
 *
 * animevietsub.li sits behind a JS challenge that answers a datacenter or
 * emulator IP with a 403, so a device run of the detail page proves nothing
 * about the source — the request may simply never arrive. Replaying the real
 * captures locally removes the site from the equation and leaves exactly the
 * thing worth testing: that the wasm builds, upgrades and hands back what the
 * app then renders.
 *
 * What this covers that the 73 in-crate tests cannot: that `get_anime_update`
 * survives the real host and the real ABI, and that the `extra` bag a detail
 * page stashes actually arrives in Kotlin intact — it is a `HashMap` on a
 * postcard-encoded struct, so a mismatch shows up nowhere else.
 *
 * The pages are the source's own `tests/fixtures/page_*.html` captures, served
 * verbatim, so the selectors under test are the selectors the site really
 * served rather than hand-written markup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnimevietsubSourceRunnerIntegrationTest {
    private lateinit var host: KrxHostImpl
    private lateinit var runner: KomoreiRunner
    private lateinit var fixture: FixtureServer

    companion object {
        private val animevietsubKrx: String =
            System.getProperty("komorei.test.animevietsubKrx")
                ?: error("missing -Dkomorei.test.animevietsubKrx (set by app/build.gradle.kts)")

        /**
         * The source's captured pages, replayed byte for byte.
         *
         * Read eagerly in the server's constructor rather than per request: a
         * missing path is a setup mistake, and serving an empty body for it
         * turns into an opaque source error ten steps away.
         */
        private val FIXTURES: String =
            System.getProperty("komorei.test.animevietsubFixtures")
                ?: error("missing -Dkomorei.test.animevietsubFixtures (set by app/build.gradle.kts)")

        private fun fixture(name: String): String = File(FIXTURES, name).readText()

        /** A card key the captured front page links to, for the upgrade path. */
        private const val ANIME_KEY = "thieu-chu-gioi-chay-tron-2024-a5304"
    }

    @Before
    fun setUp() {
        assertNotNull(
            "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
            System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        host = KrxHostImpl(context)
        fixture = FixtureServer()
        // The source exposes `base_url` as a user default, so pointing it at the
        // fixture also proves the settings → request wiring.
        host.defaultsSet(
            "base_url",
            git.shin.komorei.sdk.runner.HostDefaultValue
                .String(fixture.baseUrl),
        )
        runner = KrxManager.load(host, File(animevietsubKrx).readBytes())
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun `home builds from the captured front page`() {
        val home = runner.home()
        val titles = home.components.map { it.title.orEmpty() }
        assertTrue(
            "expected a launcher strip and several rails, got $titles",
            titles.contains("Duyệt nhanh") && titles.count { it.isNotEmpty() } >= 5,
        )
        // The first row the source asks the site for, so a selector regression
        // cannot hide behind a later rail still matching.
        val scroller = home.components.first().let { it }
        assertNotNull(scroller.title)
    }

    @Test
    fun `the detail upgrade fills the fields the detail screen renders`() {
        val lite =
            RunnerAnime(
                key = ANIME_KEY,
                sourceId = "vi.animevietsub",
                title = "Lite card title",
                originalTitle = "",
                cover = "https://example.test/cover.jpg",
                banner = null,
                description = null,
                episodeCount = 0,
                currentEpisode = null,
                rating = null,
                ratingCount = null,
                status = git.shin.komorei.sdk.runner.AnimeStatus.UNKNOWN,
                releaseYear = null,
                genres = emptyList(),
                authors = emptyList(),
                studio = null,
                seasonOf = null,
                countries = emptyList(),
                isFeatured = false,
                views = 0,
                nextEpisodeAirInfo = null,
                qualityTag = null,
                seasons = emptyList(),
                episodes = null,
                url = null,
                extra = emptyMap(),
            )

        val full = runner.animeUpdate(lite, true, false)

        // The screen shows a title, a rating and a rating count; a Lite card has
        // none of them, so these prove the upgrade ran rather than returning
        // the input unchanged.
        assertTrue("title not upgraded: '${full.title}'", full.title.isNotEmpty())
        assertTrue("title still the Lite one", full.title != lite.title)
        assertNotNull("no rating parsed", full.rating)
        assertTrue("no genres parsed", full.genres.isNotEmpty())
        assertNotNull("no studio parsed", full.studio)
    }

    @Test
    fun `the detail page hands its recommendation rail over in extra`() {
        val lite =
            RunnerAnime(
                key = ANIME_KEY,
                sourceId = "vi.animevietsub",
                title = "Lite card title",
                originalTitle = "",
                cover = "https://example.test/cover.jpg",
                banner = null,
                description = null,
                episodeCount = 0,
                currentEpisode = null,
                rating = null,
                ratingCount = null,
                status = git.shin.komorei.sdk.runner.AnimeStatus.UNKNOWN,
                releaseYear = null,
                genres = emptyList(),
                authors = emptyList(),
                studio = null,
                seasonOf = null,
                countries = emptyList(),
                isFeatured = false,
                views = 0,
                nextEpisodeAirInfo = null,
                qualityTag = null,
                seasons = emptyList(),
                episodes = null,
                url = null,
                extra = emptyMap(),
            )

        val full = runner.animeUpdate(lite, true, false)

        // `extra` is a HashMap on a postcard-encoded struct: it crosses the wasm
        // boundary, is converted to a `Map<String, String>` in Kotlin, and comes
        // back. Nothing else in the app would notice if a field were dropped.
        val stash = full.extra["avs.recommendations"]
        assertNotNull("the detail page's recommendation rail was not stashed: ${full.extra}", stash)
        val json = requireNotNull(stash)
        assertTrue("the stash is empty: $json", json.length > 2)
        assertTrue("the stash is not the card list: $json", json.contains("\"key\""))
    }

    @Test
    fun `recommended anime is served from the stash without a second request`() {
        val lite =
            RunnerAnime(
                key = ANIME_KEY,
                sourceId = "vi.animevietsub",
                title = "Lite card title",
                originalTitle = "",
                cover = "https://example.test/cover.jpg",
                banner = null,
                description = null,
                episodeCount = 0,
                currentEpisode = null,
                rating = null,
                ratingCount = null,
                status = git.shin.komorei.sdk.runner.AnimeStatus.UNKNOWN,
                releaseYear = null,
                genres = emptyList(),
                authors = emptyList(),
                studio = null,
                seasonOf = null,
                countries = emptyList(),
                isFeatured = false,
                views = 0,
                nextEpisodeAirInfo = null,
                qualityTag = null,
                seasons = emptyList(),
                episodes = null,
                url = null,
                extra = emptyMap(),
            )
        val detail = runner.animeUpdate(lite, true, false)
        val before = fixture.requestCount()

        // The app hands the upgraded anime straight back, extras included.
        val recommended = runner.recommendedAnime(detail)

        assertFalse("the site's own rail produced no recommendations", recommended.entries.isEmpty())
        assertEquals(
            "recommendations must be served from the stash, not re-fetched",
            before,
            fixture.requestCount(),
        )
        // The anime being viewed is never a recommendation of itself.
        assertTrue(
            "the section recommends the anime already open",
            recommended.entries.none { it.key == ANIME_KEY },
        )
        assertTrue(
            "a recommendation the UI cannot render",
            recommended.entries.all { it.title.isNotEmpty() && it.cover.isNotEmpty() },
        )
    }

    // ── animevietsub.li fixture server (plain ServerSocket HTTP) ─────────────

    private class FixtureServer : Closeable {
        private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))

        @Volatile private var closed = false
        val requests = CopyOnWriteArrayList<String>()
        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

        /**
         * Every page the source can ask for, loaded up front.
         *
         * Loading here rather than per request means a wrong fixtures path fails
         * the test where the mistake is, instead of closing connections with no
         * body — which reaches the source as a plain "cannot connect" and reads
         * like a networking problem.
         */
        private val pages: Map<String, String> =
            mapOf(
                "/" to "page_home.html",
                "/index.php" to "page_home.html",
                "/bang-xep-hang/" to "page_ranking.html",
                "/phim/" to "page_detail.html",
                "/danh-sach/" to "page_catalog.html",
                "/tim-kiem/" to "page_search.html",
            ).mapValues { (_, file) -> fixture(file) }

        /** Prefix → page, longest first: `/` would otherwise swallow every url. */
        private val byPrefix: List<Pair<String, String>> =
            pages.entries.sortedByDescending { it.key.length }.map { it.key to it.value }

        init {
            Thread({ acceptLoop() }, "avs-fixture").apply {
                isDaemon = true
                start()
            }
        }

        fun requestCount(): Int = requests.size

        override fun close() {
            closed = true
            runCatching { server.close() }
        }

        private fun acceptLoop() {
            while (!closed) {
                val sock =
                    try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                Thread({ handle(sock) }, "avs-fixture-handler").apply {
                    isDaemon = true
                    start()
                }
            }
        }

        private fun handle(sock: Socket) {
            try {
                sock.use { s ->
                    val input = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val requestLine = input.readLine() ?: return
                    val target = requestLine.split(" ").getOrNull(1) ?: "/"
                    var line = input.readLine()
                    while (line != null && line.isNotEmpty()) line = input.readLine()
                    requests += target

                    val page = route(target)
                    val bytes = (page ?: "").toByteArray(Charsets.UTF_8)
                    val out = s.getOutputStream()
                    // A 404 rather than an empty 200: an unrouted url is a gap in
                    // the fixture, and saying so beats a source that quietly
                    // parses nothing.
                    val status = if (page == null) "404 Not Found" else "200 OK"
                    out.write(
                        (
                            "HTTP/1.1 $status\r\n" +
                                "Content-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${bytes.size}\r\n" +
                                "Connection: close\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII),
                    )
                    out.write(bytes)
                    out.flush()
                }
            } catch (e: Exception) {
                // fixture: swallow per-connection errors
            }
        }

        /**
         * Route the source's own urls to the captured page each one produced.
         *
         * The detail capture is served for every `/phim/…` path, including the
         * episode list, because the two requests are independent and a 404 on
         * either would only prove the fixture is wrong.
         */
        private fun route(target: String): String? {
            val path = target.substringBefore('?')
            return pages[path] ?: byPrefix.firstOrNull { path.startsWith(it.first) }?.second
        }
    }
}
