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
import git.shin.komorei.sdk.runner.Listing
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
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File

/**
 * The REAL Nguồn C source (`sources/sources/vi.nguonc` → `package.krx`) driven
 * through the real runner (uniffi bindings + linux cdylib) and the real Kotlin
 * host ([KrxHostImpl] — Jsoup + OkHttp + SharedPreferences), exactly like
 * [OphimSourceRunnerIntegrationTest]. The krx is located via
 * `-Dkomorei.test.nguoncKrx` (set by app/build.gradle.kts).
 *
 * The network side is a LOCAL fixture server (plain `ServerSocket` HTTP):
 *  - the nguonc REST API: `/api/films/…` list/search envelopes with `paginate`
 *    and `/api/film/{slug}` detail with `movie.episodes[].items[].embed`;
 *  - the streamc.xyz embed player grants: `POST /embed.php?hash=…` returns the
 *    `bootstrap` JWT on `action=bootstrap` and the signed `playlist` URL on
 *    `action=issue` (the two-step grant the source replays in `get_stream`).
 *
 * `base_url` is pointed at the fixture through the source's own user default,
 * which proves the settings → requests wiring as well as the wasm behaviour:
 * the list/detail envelopes, season-per-server keys, the POST grant flow with
 * the embed `Origin`/`Referer`, the media `Referer` for the signed playlist and
 * error bails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NguoncSourceRunnerIntegrationTest {

    private lateinit var host: KrxHostImpl
    private lateinit var runner: git.shin.komorei.sdk.runner.KomoreiRunner
    private lateinit var fixture: FixtureServer

    companion object {
        private val nguoncKrx: String = System.getProperty("komorei.test.nguoncKrx")
            ?: error("missing -Dkomorei.test.nguoncKrx (set by app/build.gradle.kts)")
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
        runner = KrxManager.load(host, File(nguoncKrx).readBytes())
    }

    // ── search ──────────────────────────────────────────────────────────────

    @Test
    fun `search reads the list envelope and paginates through paginate`() {
        val page1 = runner.search(null, 1, emptyList())
        assertEquals("/api/films/phim-moi-cap-nhat?page=1", fixture.lastRequest())
        assertEquals(2, page1.entries.size)
        assertTrue(page1.hasNextPage) // paginate current_page 1 < total_page 2

        val first = page1.entries.first()
        assertEquals("nguoi-nhan", first.key)
        assertEquals("vi.nguonc", first.sourceId)
        assertEquals("Người Nhện", first.title)
        assertEquals("Spider-Man", first.originalTitle)
        assertEquals("2026", first.releaseYear!!.name) // list `year` string
        assertEquals(24, first.episodeCount)
        assertEquals("Tập 2", first.currentEpisode)
        assertEquals("FHD", first.qualityTag)
        assertEquals("${fixture.baseUrl}/phim/nguoi-nhan", first.url)

        val page2 = runner.search(null, 2, emptyList())
        assertEquals("/api/films/phim-moi-cap-nhat?page=2", fixture.lastRequest())
        assertTrue(page2.entries.isEmpty())
        assertFalse(page2.hasNextPage) // current_page == total_page
    }

    @Test
    fun `keyword search percent-encodes the query`() {
        val results = runner.search("Người nhện", 1, emptyList())
        val target = fixture.lastRequest()
        assertTrue("routed to /api/films/search", target.startsWith("/api/films/search?"))
        assertTrue("keyword percent-encoded", target.contains("keyword=Ng%C6%B0%E1%BB%9Di%20nh%E1%BB%87n"))
        assertTrue(target.contains("page=1"))
        assertEquals(2, results.entries.size)
    }

    @Test
    fun `genre filter routes to the-loai with a catalog slug`() {
        runner.search(
            null, 2,
            listOf(FilterValue.MultiSelect("genre", listOf("Kinh Dị"), emptyList())),
        )
        assertEquals("/api/films/the-loai/kinh-di?page=2", fixture.lastRequest())
    }

    // ── home ────────────────────────────────────────────────────────────────

    @Test
    fun `home assembles the rails plus genre chips and listing links`() {
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

        // 1. ImageScroller banner
        val promo = home.components[0].value as git.shin.komorei.sdk.runner.HomeComponentValue.ImageScroller
        assertEquals(2, promo.links.size)
        assertEquals(800, promo.width)
        assertEquals(450, promo.height)
        assertTrue(promo.links[0].value is LinkValue.Anime)

        // 2. AnimeEpisodeList — recent updates with episode number + date
        val updates = home.components[1].value as git.shin.komorei.sdk.runner.HomeComponentValue.AnimeEpisodeList
        assertEquals(2, updates.entries.size)
        assertEquals("latest", updates.listing!!.id)
        assertEquals("2", updates.entries[0].episode.episodeNumber) // "Tập 2"
        assertEquals(Instant.parse("2026-09-21T18:52:19.000Z").toEpochMilli(), updates.entries[0].episode.dateUploaded)

        // 3. Scroller — Phim Bộ
        val bo = home.components[2].value as git.shin.komorei.sdk.runner.HomeComponentValue.Scroller
        assertEquals("phim-bo", bo.listing!!.id)
        assertEquals("Tập 2", bo.entries[0].subtitle)

        // 4. AnimeList — Phim Lẻ, not ranked
        val le = home.components[3].value as git.shin.komorei.sdk.runner.HomeComponentValue.AnimeList
        assertFalse(le.ranking)
        assertNull(le.pageSize)
        assertEquals("phim-le", le.listing!!.id)
        assertEquals(1, le.entries.size)
        assertEquals("Kim Loại", le.entries[0].title)

        // 5. Filters — one chip per genre (22 catalog entries)
        val chips = home.components[4].value as git.shin.komorei.sdk.runner.HomeComponentValue.Filters
        assertEquals(22, chips.v1.size)
        assertTrue(chips.v1.all { it.values != null })
        assertEquals("Hành Động", chips.v1.first().title)

        // 6. Links — listing quick starts
        val links = home.components[5].value as git.shin.komorei.sdk.runner.HomeComponentValue.Links
        assertEquals(5, links.v1.size)
        assertEquals("latest", (links.v1.first().value as LinkValue.Listing).v1.id)
    }

    // ── anime update ────────────────────────────────────────────────────────

    @Test
    fun `anime update fills details category groups and one season per server`() {
        val lite = runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" }
        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = false)

        assertEquals("Người Nhện", full.title)
        assertEquals("Spider-Man", full.originalTitle)
        assertEquals("Mô tả phim.", full.description)
        assertEquals(AnimeStatus.ONGOING, full.status) // "Đang chiếu" format group
        assertEquals("FHD", full.qualityTag)
        assertEquals("2023", full.releaseYear!!.name) // "Năm" category group
        assertEquals(listOf("Hành Động", "Viễn Tưởng"), full.genres.map { it.name })
        assertEquals("Jon Watts", full.authors.single().name)
        assertEquals("Mỹ", full.countries.single().name)
        assertEquals("${fixture.baseUrl}/phim/nguoi-nhan", full.url)

        // both servers have 2 episodes — stable sort keeps the source order
        assertEquals(
            listOf("nguoi-nhan|Thuyết minh #1", "nguoi-nhan|Vietsub #1"),
            full.seasons.map { it.animeId },
        )
        assertEquals("Thuyết minh #1", full.seasons[0].title)
    }

    @Test
    fun `chapters scope to the season-selected server through the piped key`() {
        // the app calls getAnimeUpdate with anime.key = season.animeId ("{slug}|{server}")
        val vietsub = runner.animeUpdate(stubAnime("nguoi-nhan|Vietsub #1"), needsDetails = false, needsChapters = true)
        val eps = vietsub.episodes!!
        assertEquals(listOf("tap-1", "tap-2"), eps.map { it.key })
        assertEquals(listOf("1", "2"), eps.map { it.episodeNumber })
        assertNull(eps[0].url) // streams are resolved per-get_stream

        val thuyetMinh = runner.animeUpdate(stubAnime("nguoi-nhan|thuyết minh #1"), needsDetails = false, needsChapters = true)
        assertEquals(listOf("tap-1", "tap-2"), thuyetMinh.episodes!!.map { it.key })

        // no server in the key → first (source-order) server
        val default = runner.animeUpdate(stubAnime("nguoi-nhan"), needsDetails = false, needsChapters = true)
        assertEquals(2, default.episodes!!.size)
    }

    // ── streams ─────────────────────────────────────────────────────────────

    @Test
    fun `stream list serves one StreamInfo per server with the detail quality`() {
        val full = runner.animeUpdate(
            runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" },
            needsDetails = true, needsChapters = false,
        )
        val servers = runner.streamList(full, ep("tap-1", "1"))
        assertEquals(listOf("Thuyết minh #1", "Vietsub #1"), servers.map { it.key })
        assertTrue(servers.all { it.quality == "FHD" })
    }

    @Test
    fun `stream walks bootstrap then issue to get the signed playlist with embed referer`() {
        val full = runner.animeUpdate(
            runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" },
            needsDetails = true, needsChapters = false,
        )
        val vietsub = runner.streamList(full, ep("tap-1", "1")).first { it.key == "Vietsub #1" }

        val data = runner.stream(full, ep("tap-1", "1"), vietsub)

        // the two-step grant against the embed endpoint (detail fetch first)
        val embedPosts = fixture.posts.filter { it.target.contains("embed.php") }
        assertEquals(2, embedPosts.size)
        assertEquals("bootstrap", embedPosts[0].action)
        assertEquals("issue", embedPosts[1].action)
        assertTrue(
            "grant POSTs carry the embed origin as Origin/Referer",
            embedPosts.all {
                it.origin == fixture.baseUrl && it.referer == fixture.baseUrl
            },
        )

        // signed HLS playlist with the embed origin as the media Referer
        assertEquals("${fixture.baseUrl}/signed/abc123.m3u8", data.url)
        assertEquals(StreamType.HLS, data.streamType)
        assertFalse("playable as a normal media uri", data.isContent)
        assertEquals("${fixture.baseUrl}/", data.headers["Referer"])
        assertTrue(data.subtitles.isEmpty())
    }

    @Test
    fun `stream bails with the source message when the episode is unknown`() {
        val full = runner.animeUpdate(
            runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" },
            needsDetails = true, needsChapters = false,
        )
        val vietsub = runner.streamList(full, ep("tap-1", "1")).first { it.key == "Vietsub #1" }
        try {
            runner.stream(full, ep("tap-999", "999"), vietsub)
            fail("expected RunnerException for a missing episode")
        } catch (e: RunnerException.Source) {
            assertTrue("source message surfaced: ${e.message}", e.message.contains("không có tập"))
        }
    }

    // ── filters / settings ──────────────────────────────────────────────────

    @Test
    fun `filters expose search sort type genre country and year`() {
        val byId = runner.filters().associateBy { it.id }
        assertTrue(byId.keys.containsAll(listOf("search", "sort", "type", "genre", "country", "year")))

        val genre = byId.getValue("genre").kind as FilterKind.MultiSelect
        assertTrue(genre.isGenre)
        assertTrue(genre.usesTagStyle)
        assertTrue(genre.options.contains("Kinh Dị"))
        assertEquals(22, genre.options.size)

        val country = byId.getValue("country").kind as FilterKind.MultiSelect
        assertFalse(country.isGenre)
        assertTrue(country.options.contains("Hàn Quốc"))

        val type = byId.getValue("type").kind as FilterKind.Select
        assertEquals(listOf("Tất cả", "Phim bộ", "Phim lẻ", "TV Shows", "Đang chiếu"), type.options)

        val year = byId.getValue("year").kind as FilterKind.Select
        assertEquals(12, year.options.size) // "Tất cả" + 2016..2026
        assertTrue(year.options.contains("2024"))
    }

    @Test
    fun `settings expose the base url text setting that drives requests`() {
        val byKey = runner.settings().associateBy { it.key }

        val baseUrl = byKey.getValue("base_url")
        assertEquals("Địa chỉ API Nguồn C", baseUrl.title)
        assertEquals("base_url_changed", baseUrl.notification)
        assertEquals(listOf("content", "listings"), baseUrl.refreshes)
        val text = baseUrl.value as SettingValue.Text
        assertEquals("https://phim.nguonc.com", text.default)
        assertEquals("https://phim.nguonc.com", text.placeholder)

        assertEquals("Xoá bộ nhớ nguồn", byKey.getValue("clear_cache").title)
        assertTrue(byKey.getValue("clear_cache").value is SettingValue.Button)

        // the default is present because setUp pointed base_url at the fixture
        assertEquals(
            fixture.baseUrl,
            (host.defaultsGet("base_url") as HostDefaultValue.String).v1,
        )
    }

    // ── listings / deep links / notifications / migration ──────────────────

    @Test
    fun `listings expose the rails and fetch through danh-sach paths`() {
        val listings = runner.listings()
        assertEquals(
            listOf("latest", "dang-chieu", "phim-bo", "phim-le", "tv-shows"),
            listings.map { it.id },
        )
        assertEquals(
            listOf("Mới Cập Nhật", "Đang Chiếu", "Phim Bộ", "Phim Lẻ", "TV Shows"),
            listings.map { it.name },
        )
        assertTrue(listings.all { it.kind == ListingKind.LIST })

        val bo = runner.animeList(listings[2], 1)
        assertEquals("/api/films/danh-sach/phim-bo?page=1", fixture.lastRequest())
        assertEquals(2, bo.entries.size)

        // unknown rail id → falls back to the latest listing path
        runner.animeList(Listing("zzz", "Không rõ", ListingKind.LIST), 1)
        assertEquals("/api/films/phim-moi-cap-nhat?page=1", fixture.lastRequest())
    }

    @Test
    fun `deep link resolves the film route`() {
        val anime = runner.deepLink("${fixture.baseUrl}/phim/nguoi-nhan")
        assertTrue(anime is DeepLinkResult.Anime)
        assertEquals("nguoi-nhan", (anime as DeepLinkResult.Anime).key)

        assertNull(runner.deepLink("${fixture.baseUrl}/trang-chu"))
    }

    @Test
    fun `notifications record into user defaults`() {
        runner.notify("base_url_changed")
        assertEquals("base_url_changed", (host.defaultsGet("last_notification") as HostDefaultValue.String).v1)
    }

    @Test
    fun `migration keeps NguonC identity keys`() {
        assertEquals("nguoi-nhan", runner.migrateAnime("nguoi-nhan"))
        assertEquals("tap-2", runner.migrateEpisode("nguoi-nhan", "tap-2"))
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun stubAnime(key: String) = Anime(
        key = key,
        sourceId = "vi.nguonc",
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
    )

    private fun ep(key: String, number: String) = Episode(
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

    // ── nguonc fixture server (plain ServerSocket HTTP) ─────────────────────

    private class FixtureServer : Closeable {
        private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        @Volatile private var closed = false
        val requests = CopyOnWriteArrayList<String>()
        val posts = CopyOnWriteArrayList<Post>()

        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

        class Post(val target: String, val action: String, val origin: String?, val referer: String?)

        init {
            Thread({ acceptLoop() }, "nguonc-fixture").apply { isDaemon = true; start() }
        }

        fun lastRequest(): String = requests.lastOrNull() ?: ""

        private fun acceptLoop() {
            while (!closed) {
                val sock = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                Thread({ handle(sock) }, "nguonc-fixture-handler").apply { isDaemon = true; start() }
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

                    var headers = mutableMapOf<String, String>()
                    var line = input.readLine()
                    while (line != null && line.isNotEmpty()) {
                        val idx = line.indexOf(':')
                        if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                        line = input.readLine()
                    }

                    var action: String? = null
                    if (method == "POST") {
                        val len = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = if (len > 0) {
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
                        action = when {
                            body.contains("\"action\":\"issue\"") -> "issue"
                            body.contains("\"action\":\"bootstrap\"") -> "bootstrap"
                            else -> "unknown"
                        }
                        posts += Post(target, action ?: "unknown", headers["origin"], headers["referer"])
                    }
                    requests += target

                    val body = route(target, action)
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val out = s.getOutputStream()
                    out.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
                    )
                    out.write(bytes)
                    out.flush()
                }
            } catch (e: Exception) {
                // fixture: swallow per-connection errors
            }
        }

        private fun route(target: String, action: String?): String {
            val path = target.substringBefore('?')
            return when (path) {
                "/api/films/phim-moi-cap-nhat" ->
                    if (target.contains("page=2")) list("[]", "2", "2") else list("[$A,$B]", "1", "2")
                "/api/films/danh-sach/phim-bo" -> list("[$A,$B]", "1", "1")
                "/api/films/danh-sach/phim-le" -> list("[$B]", "1", "1")
                "/api/films/search" -> list("[$A,$B]", "1", "1")
                "/api/films/the-loai/kinh-di" -> list("[$B]", "1", "1")
                "/api/film/nguoi-nhan" -> detail
                "/embed.php" -> when (action) {
                    "issue" -> issue(target)
                    else -> bootstrap(target)
                }
                else -> list("[]", "1", "1")
            }
        }

        private fun list(items: String, currentPage: String, totalPage: String) = """{"status":"success",
            |"paginate":{"current_page":$currentPage,"total_page":$totalPage,"total_items":40,"items_per_page":10},
            |"items":$items}""".trimMargin()

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
            |]}}""".trimMargin()

        private fun bootstrap(target: String) = """{"video":"abc123","nonce":"n1",
            |"bootstrap":"eyJhbGciOiJIUzI1NiJ9.fake-signature",
            |"api":"$baseUrl${target.substringBefore('?')}?${target.substringAfter('?', "")}",
            |"turnstileEnabled":false,"turnstileSiteKey":"0xfake","ads":{"enabled":false},
            |"server":{"country":"VN"},"sharedCache":false}""".trimMargin()

        private fun issue(target: String): String {
            val hash = target.substringAfter("hash=").substringBefore('&')
            return """{"playlist":"$baseUrl/signed/$hash.m3u8","playlistFormat":"hls",
                |"issuedAt":1790017945,"expiresAt":1790032345}""".trimMargin()
        }

        override fun close() {
            closed = true
            try {
                server.close()
            } catch (_: IOException) {
            }
        }

        companion object {
            private val A = """{"name":"Người Nhện","slug":"nguoi-nhan","original_name":"Spider-Man",
                |"thumb_url":"http://x/t.jpg","poster_url":"http://x/p.jpg","description":"Mô tả phim.",
                |"total_episodes":24,"current_episode":"Tập 2","time":"45 Phút/Tập","quality":"FHD",
                |"language":"Vietsub + Thuyết Minh","director":"Jon Watts","casts":"Tom Holland",
                |"modified":"2026-09-21T18:52:19.000000Z","year":"2026"}""".trimMargin()

            private val B = """{"name":"Kim Loại","slug":"kim-loai","original_name":"Metal Gear",
                |"thumb_url":"http://x/t2.jpg","poster_url":"http://x/p2.jpg","description":"Phim khoa học.",
                |"total_episodes":12,"current_episode":"Full","time":"90 Phút","quality":"HD",
                |"language":"Vietsub","director":"Jaden Smith","casts":"A B",
                |"modified":"2026-09-20T00:00:00.000000Z","year":"2022"}""".trimMargin()
        }
    }
}