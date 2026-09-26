package git.shin.komorei.sdk

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.runner.Anime
import git.shin.komorei.sdk.runner.AnimeStatus
import git.shin.komorei.sdk.runner.DeepLinkResult
import git.shin.komorei.sdk.runner.Episode
import git.shin.komorei.sdk.runner.FilterKind
import git.shin.komorei.sdk.runner.FilterValue
import git.shin.komorei.sdk.runner.HostDefaultValue
import git.shin.komorei.sdk.runner.LinkValue
import git.shin.komorei.sdk.runner.ListingKind
import git.shin.komorei.sdk.runner.RunnerException
import git.shin.komorei.sdk.runner.SettingValue
import git.shin.komorei.sdk.runner.StreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

/**
 * The REAL Nguồn Phim source (`sources/sources/vi.nguonphim` → `package.krx`)
 * driven through the real runner (uniffi bindings + linux cdylib) and the real
 * Kotlin host ([KrxHostImpl]), exactly like [NguoncSourceRunnerIntegrationTest]
 * — the two crates share one engine (nguonc-style REST envelopes + the
 * streamc.xyz bootstrap → issue embed grant), so this test asserts the
 * vi.nguonphim-specific contract (source id, default base URL, settings
 * strings) plus a smoke pass over the shared flow. The krx is located via
 * `-Dkomorei.test.nguonphimKrx` (set by app/build.gradle.kts).
 *
 * `base_url` is pointed at a local fixture server (`ServerSocket` HTTP) that
 * speaks the same JSON shape as the (currently maintenance-bannered)
 * `api.nguonphim.net`: list/search envelopes, the detail with per-server
 * episodes, and the two-step embed grant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NguonphimSourceRunnerIntegrationTest {
    private lateinit var host: KrxHostImpl
    private lateinit var runner: git.shin.komorei.sdk.runner.KomoreiRunner
    private lateinit var fixture: FixtureServer

    companion object {
        private val nguonphimKrx: String =
            System.getProperty("komorei.test.nguonphimKrx")
                ?: error("missing -Dkomorei.test.nguonphimKrx (set by app/build.gradle.kts)")
    }

    @Before
    fun setUp() {
        val override = System.getProperty("uniffi.component.komorei_runner.libraryOverride")
        assertNotNull(
            "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
            override,
        )
        fixture = FixtureServer()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        host = KrxHostImpl(context)
        host.defaultsSet("base_url", HostDefaultValue.String(fixture.baseUrl))
        runner = KrxManager.load(host, File(nguonphimKrx).readBytes())
    }

    // ── search & listings ───────────────────────────────────────────────────

    @Test
    fun `search reads the nguonc-style envelope under the nguonphim source id`() {
        val page1 = runner.search(null, 1, emptyList())
        assertEquals("/api/films/phim-moi-cap-nhat?page=1", fixture.lastRequest())
        assertEquals(2, page1.entries.size)
        assertTrue(page1.hasNextPage) // current_page 1 < total_page 2

        val first = page1.entries.first()
        assertEquals("nguoi-nhan", first.key)
        assertEquals("vi.nguonphim", first.sourceId)
        assertEquals("Người Nhện", first.title)
        assertEquals("2026", first.releaseYear!!.name)
        assertEquals("${fixture.baseUrl}/phim/nguoi-nhan", first.url)

        val page2 = runner.search(null, 2, emptyList())
        assertEquals("/api/films/phim-moi-cap-nhat?page=2", fixture.lastRequest())
        assertTrue(page2.entries.isEmpty())
        assertFalse(page2.hasNextPage)
    }

    @Test
    fun `keyword search percent-encodes and genre filters route like nguonc`() {
        runner.search("Người nhện", 1, emptyList())
        val target = fixture.lastRequest()
        assertTrue(target.startsWith("/api/films/search?"))
        assertTrue(target.contains("keyword=Ng%C6%B0%E1%BB%9Di%20nh%E1%BB%87n"))

        runner.search(
            null,
            2,
            listOf(FilterValue.MultiSelect("genre", listOf("Kinh Dị"), emptyList())),
        )
        assertEquals("/api/films/the-loai/kinh-di?page=2", fixture.lastRequest())
    }

    @Test
    fun `home assembles the rails from three list calls`() {
        val home = runner.home()
        assertEquals(
            setOf(
                "/api/films/phim-moi-cap-nhat?page=1",
                "/api/films/danh-sach/phim-bo?page=1",
                "/api/films/danh-sach/phim-le?page=1",
            ),
            fixture.requests.toSet(),
        )
        assertEquals(
            listOf("Nổi Bật", "Mới Cập Nhật", "Phim Bộ", "Phim Lẻ", "Thể Loại", "Danh Sách"),
            home.components.map { it.title.orEmpty() },
        )
        val promo = home.components[0].value as git.shin.komorei.sdk.runner.HomeComponentValue.ImageScroller
        assertTrue(promo.links[0].value is LinkValue.Anime)
    }

    @Test
    fun `listings expose the rails and fetch danh-sach paths`() {
        val listings = runner.listings()
        assertEquals(
            listOf("latest", "dang-chieu", "phim-bo", "phim-le", "tv-shows"),
            listings.map { it.id },
        )
        assertTrue(listings.all { it.kind == ListingKind.LIST })
        runner.animeList(listings[2], 1)
        assertEquals("/api/films/danh-sach/phim-bo?page=1", fixture.lastRequest())
    }

    // ── anime update & chapters ─────────────────────────────────────────────

    @Test
    fun `anime update fills details and one season per server`() {
        val lite = runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" }
        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = false)

        assertEquals("Người Nhện", full.title)
        assertEquals("Mô tả phim.", full.description)
        assertEquals(AnimeStatus.ONGOING, full.status)
        assertEquals(listOf("Hành Động", "Viễn Tưởng"), full.genres.map { it.name })
        assertEquals("Mỹ", full.countries.single().name)

        assertEquals(
            listOf("nguoi-nhan|Thuyết minh #1", "nguoi-nhan|Vietsub #1"),
            full.seasons.map { it.animeId },
        )
    }

    @Test
    fun `chapters scope to the season-selected server through the piped key`() {
        val vietsub = runner.animeUpdate(stubAnime("nguoi-nhan|Vietsub #1"), needsDetails = false, needsChapters = true)
        assertEquals(listOf("tap-1", "tap-2"), vietsub.episodes!!.map { it.key })
        assertNull(vietsub.episodes!![0].url) // streams are resolved per-get_stream
    }

    // ── streams ─────────────────────────────────────────────────────────────

    @Test
    fun `stream walks bootstrap then issue and carries the embed referer`() {
        val full =
            runner.animeUpdate(
                runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" },
                needsDetails = true,
                needsChapters = false,
            )
        val servers = runner.streamList(full, ep("tap-1", "1"))
        assertEquals(listOf("Thuyết minh #1", "Vietsub #1"), servers.map { it.key })
        assertTrue(servers.all { it.quality == "FHD" })

        val vietsub = servers.first { it.key == "Vietsub #1" }
        val data = runner.stream(full, ep("tap-1", "1"), vietsub)

        val embedPosts = fixture.posts.filter { it.target.contains("embed.php") }
        assertEquals(2, embedPosts.size)
        assertEquals("bootstrap", embedPosts[0].action)
        assertEquals("issue", embedPosts[1].action)
        assertTrue(
            "grant POSTs carry the embed origin as Origin/Referer",
            embedPosts.all { it.origin == fixture.baseUrl && it.referer == fixture.baseUrl },
        )

        assertEquals("${fixture.baseUrl}/signed/abc123.m3u8", data.url)
        assertEquals(StreamType.HLS, data.streamType)
        assertEquals("${fixture.baseUrl}/", data.headers["Referer"])
    }

    @Test
    fun `stream bails with the source message when the episode is unknown`() {
        val full =
            runner.animeUpdate(
                runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" },
                needsDetails = true,
                needsChapters = false,
            )
        val vietsub = runner.streamList(full, ep("tap-1", "1")).first { it.key == "Vietsub #1" }
        try {
            runner.stream(full, ep("tap-999", "999"), vietsub)
            fail("expected RunnerException for a missing episode")
        } catch (e: RunnerException.Source) {
            assertTrue("source message surfaced: ${e.message}", e.message.contains("không có tập"))
        }
    }

    // ── filters / settings / deep link / migration ──────────────────────────

    @Test
    fun `settings default the base url to phim nguonc com`() {
        val byKey = runner.settings().associateBy { it.key }
        val baseUrl = byKey.getValue("base_url")
        assertEquals("Địa chỉ API Nguồn Phim", baseUrl.title)
        assertEquals(listOf("content", "listings"), baseUrl.refreshes)
        val text = baseUrl.value as SettingValue.Text
        assertEquals("https://phim.nguonc.com", text.default)
        assertEquals("https://phim.nguonc.com", text.placeholder)

        val genre =
            runner
                .filters()
                .associateBy { it.id }
                .getValue("genre")
                .kind as FilterKind.MultiSelect
        assertTrue(genre.isGenre)
        assertTrue(genre.options.contains("Kinh Dị"))

        assertEquals(
            fixture.baseUrl,
            (host.defaultsGet("base_url") as HostDefaultValue.String).v1,
        )
    }

    @Test
    fun `deep link resolves the film route and migration keeps identity keys`() {
        val anime = runner.deepLink("${fixture.baseUrl}/phim/nguoi-nhan")
        assertTrue(anime is DeepLinkResult.Anime)
        assertEquals("nguoi-nhan", (anime as DeepLinkResult.Anime).key)

        assertEquals("nguoi-nhan", runner.migrateAnime("nguoi-nhan"))
        assertEquals("tap-2", runner.migrateEpisode("nguoi-nhan", "tap-2"))
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun stubAnime(key: String) =
        Anime(
            key = key,
            sourceId = "vi.nguonphim",
            title = key,
            originalTitle = "",
            cover = "",
            banner = null,
            description = null,
            episodeCount = 0,
            currentEpisode = null,
            rating = null,
            ratingCount = null,
            status = AnimeStatus.UNKNOWN,
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

    private fun ep(
        key: String,
        number: String,
    ) = Episode(
        key = key,
        episodeNumber = number,
        title = null,
        dateUploaded = null,
        thumbnail = null,
        quality = null,
        durationSeconds = null,
        url = null,
        language = null,
        locked = false,
    )

    // ── fixture server (same JSON shape as the Nguồn API) ───────────────────

    private class FixtureServer : Closeable {
        private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))

        @Volatile private var closed = false
        val requests = CopyOnWriteArrayList<String>()
        val posts = CopyOnWriteArrayList<Post>()

        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

        class Post(
            val target: String,
            val action: String,
            val origin: String?,
            val referer: String?,
        )

        init {
            Thread({ acceptLoop() }, "nguonphim-fixture").apply {
                isDaemon = true
                start()
            }
        }

        fun lastRequest(): String = requests.lastOrNull() ?: ""

        private fun acceptLoop() {
            while (!closed) {
                val sock =
                    try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                Thread({ handle(sock) }, "nguonphim-fixture-handler").apply {
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

                    val headers = mutableMapOf<String, String>()
                    var line = input.readLine()
                    while (line != null && line.isNotEmpty()) {
                        val idx = line.indexOf(':')
                        if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                        line = input.readLine()
                    }

                    var action: String? = null
                    if (method == "POST") {
                        val len = headers["content-length"]?.toIntOrNull() ?: 0
                        val body =
                            if (len > 0) {
                                val chars = CharArray(len)
                                var total = 0
                                while (total < len) {
                                    val r = input.read(chars, total, len - total)
                                    if (r < 0) break
                                    total += r
                                }
                                String(chars)
                            } else {
                                ""
                            }
                        action =
                            when {
                                body.contains("\"action\":\"issue\"") -> "issue"
                                body.contains("\"action\":\"bootstrap\"") -> "bootstrap"
                                else -> "unknown"
                            }
                        posts += Post(target, action, headers["origin"], headers["referer"])
                    }
                    requests += target

                    val body = route(target, action)
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val out = s.getOutputStream()
                    out.write(
                        (
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json; charset=utf-8\r\n" +
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

        private fun route(
            target: String,
            action: String?,
        ): String {
            val path = target.substringBefore('?')
            return when (path) {
                "/api/films/phim-moi-cap-nhat" ->
                    if (target.contains("page=2")) list("[]", "2", "2") else list("[$A,$B]", "1", "2")
                "/api/films/danh-sach/phim-bo" -> list("[$A,$B]", "1", "1")
                "/api/films/danh-sach/phim-le" -> list("[$B]", "1", "1")
                "/api/films/search" -> list("[$A,$B]", "1", "1")
                "/api/films/the-loai/kinh-di" -> list("[$B]", "1", "1")
                "/api/film/nguoi-nhan" -> detail
                "/embed.php" ->
                    when (action) {
                        "issue" -> issue(target)
                        else -> bootstrap(target)
                    }
                else -> list("[]", "1", "1")
            }
        }

        private fun list(
            items: String,
            currentPage: String,
            totalPage: String,
        ) = """{"status":"success",
            |"paginate":{"current_page":$currentPage,"total_page":$totalPage,"total_items":40,"items_per_page":10},
            |"items":$items}
            """.trimMargin()

        private val detail =
            """{"status":"success","movie":{
            |"id":"a1","name":"Người Nhện","slug":"nguoi-nhan","original_name":"Spider-Man",
            |"poster_url":"$baseUrl/p.jpg","thumb_url":"$baseUrl/t.jpg",
            |"description":"Mô tả phim.","created":"2026-09-10T00:00:00.000000Z","modified":"2026-09-21T18:52:19.000000Z",
            |"total_episodes":24,"current_episode":"Tập 2","time":"45 Phút/Tập","quality":"FHD",
            |"language":"Vietsub + Thuyết Minh","director":"Jon Watts","casts":"Tom Holland",
            |"category":{
            |"1":{"group":{"id":"c1","name":"Định dạng"},"list":[{"id":"x","name":"Phim bộ"},{"id":"y","name":"Đang chiếu"}]},
            |"2":{"group":{"id":"c2","name":"Thể loại"},"list":[{"id":"a","name":"Hành Động"},{"id":"b","name":"Viễn Tưởng"}]},
            |"3":{"group":{"id":"c3","name":"Năm"},"list":[{"id":"c","name":"2023"}]},
            |"4":{"group":{"id":"c4","name":"Quốc gia"},"list":[{"id":"d","name":"Mỹ"}]}
            |},
            |"episodes":[
            |{"server_name":"Thuyết minh #1","items":[
            |{"name":"1","slug":"tap-1","embed":"$baseUrl/embed.php?hash=tm1"},
            |{"name":"2","slug":"tap-2","embed":"$baseUrl/embed.php?hash=tm2"}]},
            |{"server_name":"Vietsub #1","items":[
            |{"name":"1","slug":"tap-1","embed":"$baseUrl/embed.php?hash=abc123"},
            |{"name":"2","slug":"tap-2","embed":"$baseUrl/embed.php?hash=def456"}]}
            |]}}
            """.trimMargin()

        private fun bootstrap(target: String) =
            """{"video":"abc123","nonce":"n1",
            |"bootstrap":"eyJhbGciOiJIUzI1NiJ9.fake-signature",
            |"api":"$baseUrl${target.substringBefore('?')}?${target.substringAfter('?', "")}",
            |"turnstileEnabled":false,"turnstileSiteKey":"0xfake","ads":{"enabled":false},
            |"server":{"country":"VN"},"sharedCache":false}
            """.trimMargin()

        private fun issue(target: String): String {
            val hash = target.substringAfter("hash=").substringBefore('&')
            return """{"playlist":"$baseUrl/signed/$hash.m3u8","playlistFormat":"hls",
                |"issuedAt":1790017945,"expiresAt":1790032345}
                """.trimMargin()
        }

        override fun close() {
            closed = true
            try {
                server.close()
            } catch (_: IOException) {
            }
        }

        companion object {
            private val A =
                """{"name":"Người Nhện","slug":"nguoi-nhan","original_name":"Spider-Man",
                |"thumb_url":"http://x/t.jpg","poster_url":"http://x/p.jpg","description":"Mô tả phim.",
                |"total_episodes":24,"current_episode":"Tập 2","time":"45 Phút/Tập","quality":"FHD",
                |"language":"Vietsub + Thuyết Minh","director":"Jon Watts","casts":"Tom Holland",
                |"modified":"2026-09-21T18:52:19.000000Z","year":"2026"}
                """.trimMargin()

            private val B =
                """{"name":"Kim Loại","slug":"kim-loai","original_name":"Metal Gear",
                |"thumb_url":"http://x/t2.jpg","poster_url":"http://x/p2.jpg","description":"Phim khoa học.",
                |"total_episodes":12,"current_episode":"Full","time":"90 Phút","quality":"HD",
                |"language":"Vietsub","director":"Jaden Smith","casts":"A B",
                |"modified":"2026-09-20T00:00:00.000000Z","year":"2022"}
                """.trimMargin()
        }
    }
}
