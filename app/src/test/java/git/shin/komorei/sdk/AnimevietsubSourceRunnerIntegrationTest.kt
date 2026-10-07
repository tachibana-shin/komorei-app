package git.shin.komorei.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.runner.Episode
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
 * What this covers that the 76 in-crate tests cannot: that `get_anime_update`
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

        /**
         * The signed episode pair from the episode capture: `data-id` and
         * `data-hash` of its first anchor, which is what `POST /ajax/player`
         * demands and what the redirect test asserts arrives byte for byte.
         */
        private const val EPISODE_ID = "114607"
        private const val EPISODE_HASH = "5qC6TJh-JJ3nEYX3062ISEf_AWJ8gq86y_9BYWB52I8FR393Aoj-WkLT2um5GIrIqWMoz-xLcX5uCp83IJ0oxePHQ9sVCqVXAYphxL2cfzMsXXp6c8iZmpZJEzgAWlqV"
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
        val lite = liteAnime()

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
        val lite = liteAnime()

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
        val lite = liteAnime()
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

    /**
     * The signed player call survives a base host that answers 301.
     *
     * The site moved from `animevietsub.li` to `animevietsub.nl` and kept the
     * old host answering 301 on **every** path rather than going dark. Both
     * OkHttp and a browser follow that the way RFC 7231 says — by repeating a
     * redirected POST as a GET *with no body* — so the signed `id`/`link` pair
     * never arrived, the endpoint answered for a request the source had not
     * made, and the parse failure that came back was the bare "trả về dữ liệu
     * không hợp lệ" with nothing to say why. The fixture reproduces the
     * redirect; the assertions are the POST arriving intact at the url it was
     * redirected to, and the `link` that answer carries being fetched next.
     */
    @Test
    fun `the signed player POST survives a redirecting base host`() {
        val anime = liteAnime()
        val episode =
            Episode(
                key = "1-$EPISODE_ID-$EPISODE_HASH",
                episodeNumber = "1",
                title = null,
                dateUploaded = null,
                thumbnail = null,
                quality = null,
                durationSeconds = null,
                url = null,
                language = null,
                locked = false,
            )
        val server = runner.streamList(anime, episode).first()

        val failure = runCatching { runner.stream(anime, episode, server) }.exceptionOrNull()

        assertTrue(
            "the signed POST never reached the moved endpoint: ${fixture.posts}",
            fixture.posts.any {
                it.first == "/ajax/player-v2" && it.second == "id=$EPISODE_ID&link=$EPISODE_HASH"
            },
        )
        // The link that POST answered with is fetched next, which only happens
        // once its JSON parsed — the empty body a downgraded GET gets fails
        // long before this.
        assertTrue(
            "the link from /ajax/player-v2 was never fetched: ${fixture.requests}",
            fixture.requests.contains("/player.html"),
        )
        val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(
            "the player step still reports the parse failure: $messages",
            messages.none { it.contains("trả về dữ liệu không hợp lệ") },
        )
    }

    /**
     * The Lite card before any upgrade, shared by every test here: what is
     * under test is what the source does with it, not which fields a stub
     * happens to carry.
     */
    private fun liteAnime() =
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

    // ── animevietsub.li fixture server (plain ServerSocket HTTP) ─────────────

    private class FixtureServer : Closeable {
        private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))

        @Volatile private var closed = false
        val requests = CopyOnWriteArrayList<String>()

        /** Every POST as `path` → body, so a test can assert what was sent. */
        val posts = CopyOnWriteArrayList<Pair<String, String>>()
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
                    val parts = requestLine.split(" ")
                    val method = parts.getOrNull(0) ?: "GET"
                    val target = parts.getOrNull(1) ?: "/"
                    var contentLength = 0
                    var line = input.readLine()
                    while (line != null && line.isNotEmpty()) {
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                        line = input.readLine()
                    }
                    // ISO-8859-1 is byte-preserving, so the N chars read here
                    // are exactly the N bytes of the form body the source
                    // signed. (`Reader.readNChars` is not in the android.jar
                    // this compiles against, hence the manual loop.)
                    val bodyChars = CharArray(contentLength)
                    var bodyRead = 0
                    while (bodyRead < contentLength) {
                        val read = input.read(bodyChars, bodyRead, contentLength - bodyRead)
                        if (read <= 0) break
                        bodyRead += read
                    }
                    val body = String(bodyChars, 0, bodyRead)
                    requests += target
                    if (method == "POST") posts += target.substringBefore('?') to body

                    val reply = route(method, target)
                    val bytes = reply.body.toByteArray(Charsets.UTF_8)
                    val headers =
                        listOf(
                            "Content-Type: text/html; charset=utf-8",
                            "Content-Length: ${bytes.size}",
                            "Connection: close",
                        ) + reply.headers
                    val out = s.getOutputStream()
                    // A 404 rather than an empty 200: an unrouted url is a gap in
                    // the fixture, and saying so beats a source that quietly
                    // parses nothing.
                    out.write(
                        (
                            "HTTP/1.1 ${reply.status}\r\n" +
                                headers.joinToString("\r\n") { it } +
                                "\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII),
                    )
                    out.write(bytes)
                    out.flush()
                }
            } catch (e: Exception) {
                // fixture: swallow per-connection errors
            }
        }

        /** A routed answer: status line, extra headers, body. */
        private data class Reply(
            val status: String = "200 OK",
            val headers: List<String> = emptyList(),
            val body: String = "",
        )

        /**
         * Route the source's own urls to the captured page each one produced,
         * plus the `POST /ajax/player` handshake.
         *
         * The detail capture is served for every `/phim/…` path, including the
         * episode list, because the two requests are independent and a 404 on
         * either would only prove the fixture is wrong.
         */
        private fun route(
            method: String,
            target: String,
        ): Reply {
            val path = target.substringBefore('?')
            // The site's own redirect: the superseded host answers 301 to the
            // live one instead of going dark, and that is exactly what breaks a
            // POST — both OkHttp and a browser repeat it as a GET *with no
            // body*, so the endpoint answers for a request the source never
            // made. Both halves are reproduced here, including the empty body
            // the downgraded GET gets: what the source used to JSON-parse.
            if (path == "/ajax/player" && method == "POST") {
                return Reply(
                    status = "301 Moved Permanently",
                    headers = listOf("Location: $baseUrl/ajax/player-v2"),
                    body = "<html><head><title>301 Moved Permanently</title></head></html>",
                )
            }
            if (path == "/ajax/player-v2") {
                return if (method == "POST") {
                    Reply(
                        body =
                            """{"_fxStatus":1,"success":1,"title":"AnimeVsub",""" +
                                """"link":"$baseUrl/player.html","playTech":"iframe"}""",
                    )
                } else {
                    Reply()
                }
            }
            // The handshake's `link` is fetched — the test asserts on that — but
            // deliberately not served: what is under test ends there, and past
            // it the playlist fetch asks for https, which this plain-HTTP
            // fixture can only stall on.
            if (path == "/player.html") return Reply(status = "404 Not Found")

            val page = pages[path] ?: byPrefix.firstOrNull { path.startsWith(it.first) }?.second
            return if (page == null) Reply(status = "404 Not Found") else Reply(body = page)
        }
    }
}
