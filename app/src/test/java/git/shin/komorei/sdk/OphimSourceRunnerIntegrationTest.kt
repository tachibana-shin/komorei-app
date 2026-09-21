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
 * The REAL OPhim source (`sources/ophim` → `package.krx`) driven through the
 * real runner (uniffi bindings + linux cdylib) and the real Kotlin host
 * ([KrxHostImpl] — Jsoup + OkHttp + SharedPreferences), exactly like
 * [FakeViSourceRunnerIntegrationTest]. The krx is located via
 * `-Dkomorei.test.ophimKrx` (set by app/build.gradle.kts).
 *
 * `ophim1.com` (and every classic OPhim mirror) rotates and dies, so the
 * network side is a LOCAL classic-OPHIM fixture server (plain `ServerSocket`
 * HTTP responses, no `jdk.httpserver` module needed). `base_url` is pointed at
 * the fixture through the source's own `base_url` user default, which proves
 * the whole settings → requests wiring as well as the wasm behaviour:
 * classic `data.items`/`data.item` envelopes, flat `items`/`movie` fork
 * envelopes, season-per-server keys, stream fallbacks and error bails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OphimSourceRunnerIntegrationTest {

    private lateinit var host: KrxHostImpl
    private lateinit var runner: git.shin.komorei.sdk.runner.KomoreiRunner
    private lateinit var fixture: FixtureServer

    companion object {
        private val ophimKrx: String = System.getProperty("komorei.test.ophimKrx")
            ?: error("missing -Dkomorei.test.ophimKrx (set by app/build.gradle.kts)")
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
        // The source reads `base_url` on every request from the host defaults
        // — pointing it at the fixture is itself part of what we assert.
        host.defaultsSet("base_url", HostDefaultValue.String(fixture.baseUrl))
        runner = KrxManager.load(host, File(ophimKrx).readBytes())
    }

    // ── search ──────────────────────────────────────────────────────────────

    @Test
    fun `browse search reads the classic data-items envelope and pagination`() {
        val page1 = runner.search(null, 1, emptyList())
        assertEquals("/danh-sach/phim-moi-cap-nhat?page=1", fixture.lastRequest())
        assertEquals(2, page1.entries.size)
        assertTrue(page1.hasNextPage) // pagination currentPage 1 < totalPages 2

        val first = page1.entries.first()
        assertEquals("nguoi-nhan", first.key)
        assertEquals("vi.ophim", first.sourceId)
        assertEquals("Người Nhện", first.title)
        assertEquals("Spider-Man", first.originalTitle)
        assertEquals("https://img.ophim.com/nguoi-nhan.jpg", first.cover) // protocol-relative absolutized
        assertEquals(AnimeStatus.ONGOING, first.status)
        assertEquals("Tập 2/24", first.currentEpisode)
        assertEquals(24, first.episodeCount)
        assertEquals("FHD", first.qualityTag)
        assertTrue(first.genres.map { it.name }.containsAll(listOf("Hành Động", "Viễn Tưởng")))
        assertEquals("Mỹ", first.countries.single().name)
        assertEquals("2023", first.releaseYear!!.name)

        val page2 = runner.search(null, 2, emptyList())
        assertEquals("/danh-sach/phim-moi-cap-nhat?page=2", fixture.lastRequest())
        assertTrue(page2.entries.isEmpty())
        assertFalse(page2.hasNextPage) // currentPage == totalPages
    }

    @Test
    fun `keyword search uses the flat items envelope and percent-encodes the query`() {
        val results = runner.search("Người nhện", 1, emptyList())
        val target = fixture.lastRequest()
        assertTrue("routed to /tim-kiem", target.startsWith("/tim-kiem?"))
        assertTrue("keyword percent-encoded", target.contains("keyword=Ng%C6%B0%E1%BB%9Di%20nh%E1%BB%87n"))
        assertTrue(target.contains("page=1"))

        // the fixture returns the same two items at the top level (no `data`
        // wrapper) — proves the flat fork envelope deserializes too.
        assertEquals(2, results.entries.size)
        assertTrue(results.entries.any { it.key == "nguoi-nhan" })
    }

    @Test
    fun `filters serialize category country type year and sort into the query`() {
        runner.search(
            null, 1,
            listOf(
                FilterValue.MultiSelect("category", listOf("Hành Động"), emptyList()),
                FilterValue.MultiSelect("country", listOf("Mỹ"), emptyList()),
                FilterValue.Select("type", "Phim bộ"),
                FilterValue.Range("year", 2023f, 2023f),
                FilterValue.Sort("sort", 1, ascending = false),
            ),
        )
        val target = fixture.lastRequest()
        assertEquals("/danh-sach/phim-moi-cap-nhat", target.substringBefore('?'))
        assertTrue(target.contains("category=hanh-dong")) // name → slug mapping
        assertTrue(target.contains("country=my"))
        assertTrue(target.contains("type=series"))
        assertTrue(target.contains("year=2023"))
        assertTrue(target.contains("sort_field=year"))
        assertTrue(target.contains("sort_type=desc"))
    }

    // ── home ────────────────────────────────────────────────────────────────

    @Test
    fun `home assembles the five data rails plus genre chips and listing shortcuts`() {
        val home = runner.home()

        // three parallel list fetches, in order
        assertEquals(
            listOf(
                "/danh-sach/phim-moi-cap-nhat?page=1",
                "/danh-sach/phim-bo?page=1",
                "/danh-sach/phim-le?page=1",
            ),
            fixture.requests.filter { it.startsWith("/danh-sach") },
        )

        assertEquals(
            listOf("Nổi Bật", "Mới Cập Nhật", "Phim Bộ", "Phim Lẻ", "Thể Loại", "Danh Sách"),
            home.components.map { it.title.orEmpty() },
        )

        // 1. ImageScroller banner
        val promo = home.components[0].value as git.shin.komorei.sdk.runner.HomeComponentValue.ImageScroller
        assertEquals(2, promo.links.size)
        assertEquals("https://img.ophim.com/nguoi-nhan.jpg", promo.links[0].imageUrl)
        assertTrue(promo.links[0].value is LinkValue.Anime)
        assertEquals(800, promo.width)
        assertEquals(450, promo.height)

        // 2. AnimeEpisodeList — recent updates with real episode number + date
        val updates = home.components[1].value as git.shin.komorei.sdk.runner.HomeComponentValue.AnimeEpisodeList
        assertEquals(2, updates.entries.size)
        assertEquals("latest", updates.listing!!.id)
        assertEquals("2", updates.entries[0].episode.episodeNumber) // "Tập 2/24"
        assertEquals(Instant.parse("2026-09-21T18:52:19.000Z").toEpochMilli(), updates.entries[0].episode.dateUploaded)

        // 3. Scroller — Phim Bộ with subtitle = episode_current
        val bo = home.components[2].value as git.shin.komorei.sdk.runner.HomeComponentValue.Scroller
        assertEquals("bo", bo.listing!!.id)
        assertEquals("Tập 2/24", bo.entries[0].subtitle)

        // 4. AnimeList — Phim Lẻ, not ranked, no page size
        val le = home.components[3].value as git.shin.komorei.sdk.runner.HomeComponentValue.AnimeList
        assertFalse(le.ranking)
        assertNull(le.pageSize)
        assertEquals("le", le.listing!!.id)
        assertEquals(1, le.entries.size)
        assertEquals("Kim Loại", le.entries[0].title)

        // 5. Filters — one chip per genre, each with a MultiSelect value
        val chips = home.components[4].value as git.shin.komorei.sdk.runner.HomeComponentValue.Filters
        assertEquals(23, chips.v1.size)
        assertTrue(chips.v1.all { it.values != null })
        assertEquals("Hành Động", chips.v1.first().title)

        // 6. Links — listing quick starts
        val links = home.components[5].value as git.shin.komorei.sdk.runner.HomeComponentValue.Links
        assertEquals(5, links.v1.size)
        assertEquals("latest", (links.v1.first().value as LinkValue.Listing).v1.id)
    }

    // ── anime update ────────────────────────────────────────────────────────

    @Test
    fun `anime update fills details and builds one season per streaming server`() {
        val lite = runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" }
        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = false)

        assertEquals("Người Nhện", full.title)
        assertTrue("html stripped from description", full.description!!.contains("Người Nhện chiến đấu"))
        assertEquals(AnimeStatus.ONGOING, full.status)
        assertEquals("FHD", full.qualityTag)
        assertEquals("2023", full.releaseYear!!.name)
        assertEquals(listOf("Hành Động", "Viễn Tưởng"), full.genres.map { it.name })
        assertEquals("Jon Watts", full.authors.single().name)
        assertEquals("Mỹ", full.countries.single().name)
        assertEquals(2, full.episodeCount) // biggest server group wins
        assertEquals("${fixture.baseUrl}/phim/nguoi-nhan", full.url)

        // seasons == servers, sorted by episode count desc → OPhim (2) first
        assertEquals(
            listOf("nguoi-nhan|OPhim", "nguoi-nhan|Trailer"),
            full.seasons.map { it.animeId },
        )
        assertEquals("OPhim", full.seasons[0].title)
        assertEquals("nguoi-nhan|OPhim", full.seasons[0].id)
    }

    @Test
    fun `anime update fills details from the flat movie envelope`() {
        val lite = runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "kim-loai" }
        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = false)

        assertEquals("Kim Loại", full.title)
        assertEquals(AnimeStatus.COMPLETED, full.status)
        assertEquals("HD", full.qualityTag)
        assertEquals("Khoa Học", full.genres.single().name)
        assertEquals("Hàn Quốc", full.countries.single().name)
        assertEquals(12, full.episodeCount) // no servers on this fork → episode_total
        assertTrue(full.seasons.isEmpty())
    }

    @Test
    fun `chapters scope to the season-selected server through the piped key`() {
        // the app calls getAnimeUpdate with anime.key = season.animeId ("{slug}|{server}")
        val ophim = runner.animeUpdate(stubAnime("nguoi-nhan|OPhim"), needsDetails = false, needsChapters = true)
        val eps = ophim.episodes!!
        assertEquals(listOf("tap-1", "tap-2"), eps.map { it.key })
        assertEquals("1", eps[0].episodeNumber)
        assertEquals("2", eps[1].episodeNumber)
        assertEquals("Tập 2", eps[1].title)
        assertEquals("vi", eps[0].language)
        assertNull(eps[0].url) // streams are resolved per-get_stream

        val trailer = runner.animeUpdate(stubAnime("nguoi-nhan|Trailer"), needsDetails = false, needsChapters = true)
        assertEquals(listOf("trailer-1"), trailer.episodes!!.map { it.key })

        // no server in the key → default to the biggest server (OPhim)
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
        assertEquals(listOf("OPhim", "Trailer"), servers.map { it.key })
        assertEquals(listOf("OPhim", "Trailer"), servers.map { it.name })
        assertTrue(servers.all { it.quality == "FHD" })
    }

    @Test
    fun `stream resolves the m3u8 with a referer and falls back across server groups`() {
        val full = runner.animeUpdate(
            runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" },
            needsDetails = true, needsChapters = false,
        )
        val ophim = runner.streamList(full, ep("tap-1", "1")).first { it.key == "OPhim" }

        val tap1 = runner.stream(full, ep("tap-1", "1"), ophim)
        assertEquals("https://cdn.ophim.com/nguoi-nhan/tap-1.m3u8", tap1.url)
        assertEquals(StreamType.HLS, tap1.streamType)
        assertFalse("playable as a normal media uri", tap1.isContent)
        assertEquals("${fixture.baseUrl}/", tap1.headers["Referer"])
        assertTrue(tap1.subtitles.isEmpty())

        // trailer-1 only exists in the Trailer group — requested on OPhim it
        // must fall back to the group that actually has it.
        val trailer = runner.stream(full, ep("trailer-1", "1"), ophim)
        assertEquals("https://cdn.ophim.com/nguoi-nhan/trailer.mp4", trailer.url)
        assertEquals(StreamType.MP4, trailer.streamType)
    }

    @Test
    fun `stream bails with the source message when the episode is unknown`() {
        val full = runner.animeUpdate(
            runner.search("Người nhện", 1, emptyList()).entries.single { it.key == "nguoi-nhan" },
            needsDetails = true, needsChapters = false,
        )
        val ophim = runner.streamList(full, ep("tap-1", "1")).first { it.key == "OPhim" }
        try {
            runner.stream(full, ep("tap-999", "999"), ophim)
            fail("expected RunnerException for a missing episode")
        } catch (e: RunnerException.Source) {
            assertTrue("source message surfaced: ${e.message}", e.message.contains("không có tập"))
        }
    }

    // ── filters / settings ──────────────────────────────────────────────────

    @Test
    fun `filters expose the ophim search filter set`() {
        val byId = runner.filters().associateBy { it.id }
        assertTrue(byId.keys.containsAll(listOf("search", "sort", "category", "country", "type", "year")))

        val sort = byId.getValue("sort").kind as FilterKind.Sort
        assertEquals(listOf("Mới cập nhật", "Năm phát hành", "Tên A-Z"), sort.options)
        assertTrue(sort.canAscend)

        val category = byId.getValue("category").kind as FilterKind.MultiSelect
        assertTrue(category.isGenre)
        assertTrue(category.usesTagStyle)
        assertTrue(category.options.contains("Hành Động"))
        assertTrue(category.options.contains("Mecha"))

        val type = byId.getValue("type").kind as FilterKind.Select
        assertEquals(listOf("Tất cả", "Phim bộ", "Phim lẻ"), type.options)

        val year = byId.getValue("year").kind as FilterKind.Range
        assertEquals(1980f, year.min!!, 0f)
        assertEquals(2027f, year.max!!, 0f)
        assertFalse(year.decimal)
    }

    @Test
    fun `settings expose the base url text setting that drives requests`() {
        val byKey = runner.settings().associateBy { it.key }

        val baseUrl = byKey.getValue("base_url")
        assertEquals("Địa chỉ API OPhim", baseUrl.title)
        assertEquals("base_url_changed", baseUrl.notification)
        assertEquals(listOf("content", "listings"), baseUrl.refreshes)
        val text = baseUrl.value as SettingValue.Text
        assertEquals("https://phimapi.com", text.default)
        assertEquals("https://phimapi.com", text.placeholder)

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
            listOf("latest", "bo", "le", "hoat-hinh", "chieu-rap", "sap-chieu", "tv-shows"),
            listings.map { it.id },
        )
        assertEquals(listOf("Mới Cập Nhật", "Phim Bộ", "Phim Lẻ", "Hoạt Hình", "Phim Chiếu Rạp", "Sắp Chiếu", "TV Shows"),
            listings.map { it.name })
        assertTrue(listings.all { it.kind == ListingKind.LIST })

        val bo = runner.animeList(listings[1], 1)
        assertEquals("/danh-sach/phim-bo?page=1", fixture.lastRequest())
        assertEquals(2, bo.entries.size)

        // unknown rail id → falls back to the latest-listing path
        runner.animeList(Listing("zzz", "Không rõ", ListingKind.LIST), 1)
        assertEquals("/danh-sach/phim-moi-cap-nhat?page=1", fixture.lastRequest())
    }

    @Test
    fun `deep link resolves the ophim detail and watch routes`() {
        val anime = runner.deepLink("${fixture.baseUrl}/phim/nguoi-nhan")
        assertTrue(anime is DeepLinkResult.Anime)
        assertEquals("nguoi-nhan", (anime as DeepLinkResult.Anime).key)

        val episode = runner.deepLink("${fixture.baseUrl}/xem-phim/nguoi-nhan/tap-1")
        assertTrue(episode is DeepLinkResult.Episode)
        assertEquals("nguoi-nhan", (episode as DeepLinkResult.Episode).animeKey)
        assertEquals("tap-1", episode.key)

        assertNull(runner.deepLink("${fixture.baseUrl}/trang-chu"))
    }

    @Test
    fun `notifications record into user defaults`() {
        runner.notify("base_url_changed")
        assertEquals("base_url_changed", (host.defaultsGet("last_notification") as HostDefaultValue.String).v1)
    }

    @Test
    fun `migration keeps OPhim identity keys`() {
        assertEquals("nguoi-nhan", runner.migrateAnime("nguoi-nhan"))
        assertEquals("tap-2", runner.migrateEpisode("nguoi-nhan", "tap-2"))
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun stubAnime(key: String) = Anime(
        key = key,
        sourceId = "vi.ophim",
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

    // ── classic-OPHIM fixture server (plain ServerSocket HTTP) ──────────────

    private class FixtureServer : Closeable {
        private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        @Volatile private var closed = false
        val requests = CopyOnWriteArrayList<String>()
        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

        init {
            Thread({ acceptLoop() }, "ophim-fixture").apply { isDaemon = true; start() }
        }

        fun lastRequest(): String = requests.lastOrNull() ?: ""

        private fun acceptLoop() {
            while (!closed) {
                val sock = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                Thread({ handle(sock) }, "ophim-fixture-handler").apply { isDaemon = true; start() }
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

                    val body = route(target)
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

        private fun route(target: String): String {
            val path = target.substringBefore('?')
            return when (path) {
                "/danh-sach/phim-moi-cap-nhat" ->
                    if (target.contains("page=2")) classicList("[]", "2", "2")
                    else classicList("[$A,$B]", "1", "2")
                "/danh-sach/phim-bo" -> classicList("[$A,$B]", "1", "1")
                "/danh-sach/phim-le" -> classicList("[$B]", "1", "1")
                "/tim-kiem" -> flatSearch
                "/phim/nguoi-nhan" -> classicDetail
                "/phim/kim-loai" -> flatDetail
                else -> classicList("[]", "1", "1")
            }
        }

        private fun classicList(items: String, currentPage: String, totalPages: String) = """{"status":"success","msg":"",
            |"data":{"items":$items,"params":{"pagination":{"totalItems":40,"totalItemsPerPage":24,
            |"currentPage":$currentPage,"totalPages":$totalPages}}}}""".trimMargin()

        private val flatSearch = """{"status":"success","msg":"done","items":[$A,$B]}"""

        private val classicDetail =
            """{"status":"success","msg":"","data":{"item":$A_DETAIL}}"""

        private val flatDetail = """{"status":"success","movie":$B_DETAIL}"""

        override fun close() {
            closed = true
            try {
                server.close()
            } catch (_: IOException) {
            }
        }

        companion object {
            private val A = """{"_id":"a1","name":"Người Nhện","origin_name":"Spider-Man","slug":"nguoi-nhan",
                |"poster_url":"//img.ophim.com/nguoi-nhan.jpg","thumb_url":"https://img.ophim.com/nguoi-nhan-thumb.jpg",
                |"year":2023,"type":"series","quality":"FHD","lang":"Vietsub","episode_total":"24",
                |"episode_current":"Tập 2/24","status":"ongoing","director":["Jon Watts"],"actor":["Tom Holland"],
                |"category":[{"name":"Hành Động","slug":"hanh-dong"},{"name":"Viễn Tưởng","slug":"vien-tuong"}],
                |"country":[{"name":"Mỹ","slug":"my"}],"modified":{"time":"2026-09-21T18:52:19.000Z"},"view":1234}""".trimMargin()

            private val B = """{"_id":"b2","name":"Kim Loại","origin_name":"Metal Gear","slug":"kim-loai",
                |"poster_url":"https://img.ophim.com/kim-loai.jpg","thumb_url":"http://img.ophim.com/kim-loai-thumb.jpg",
                |"year":2022,"type":"series","quality":"HD","lang":"Vietsub","episode_total":"12",
                |"episode_current":"Full","status":"completed","director":["Jaden Smith"],"actor":["A","B"],
                |"category":[{"name":"Khoa Học","slug":"khoa-hoc"}],"country":[{"name":"Hàn Quốc","slug":"han-quoc"}],
                |"modified":{"time":"2026-09-20T00:00:00.000Z"},"view":900}""".trimMargin()

            private val A_DETAIL = """{"_id":"a1","name":"Người Nhện","origin_name":"Spider-Man","slug":"nguoi-nhan",
                |"poster_url":"//img.ophim.com/nguoi-nhan.jpg","thumb_url":"https://img.ophim.com/nguoi-nhan-thumb.jpg",
                |"year":2023,"type":"series","quality":"FHD","lang":"Vietsub","episode_total":"24",
                |"episode_current":"Tập 2/24","status":"ongoing","content":"<p>Người Nhện chiến đấu với tội phạm.</p>&nbsp;",
                |"director":["Jon Watts"],"actor":["Tom Holland"],
                |"category":[{"name":"Hành Động","slug":"hanh-dong"},{"name":"Viễn Tưởng","slug":"vien-tuong"}],
                |"country":[{"name":"Mỹ","slug":"my"}],"modified":{"time":"2026-09-21T18:52:19.000Z"},"view":1234,
                |"episodes":[
                |{"server_name":"OPhim","server_data":[
                |{"name":"Tập 1","slug":"tap-1","filename":"tap-1.m3u8","link_embed":"https://embed.ophim.com/1","link_m3u8":"https://cdn.ophim.com/nguoi-nhan/tap-1.m3u8"},
                |{"name":"Tập 2","slug":"tap-2","filename":"tap-2.m3u8","link_embed":"https://embed.ophim.com/2","link_m3u8":"https://cdn.ophim.com/nguoi-nhan/tap-2.m3u8"}
                |]},
                |{"server_name":"Trailer","server_data":[
                |{"name":"Trailer","slug":"trailer-1","filename":"trailer.mp4","link_embed":"https://embed.ophim.com/trailer","link_m3u8":"https://cdn.ophim.com/nguoi-nhan/trailer.mp4"}
                |]}
                |]}""".trimMargin()

            private val B_DETAIL = """{"_id":"b2","name":"Kim Loại","origin_name":"Metal Gear","slug":"kim-loai",
                |"poster_url":"https://img.ophim.com/kim-loai.jpg","thumb_url":"http://img.ophim.com/kim-loai-thumb.jpg",
                |"year":2022,"type":"series","quality":"HD","lang":"Vietsub","episode_total":"12",
                |"episode_current":"Full","status":"completed","content":"Phim khoa học viễn tưởng&hellip;",
                |"director":["Jaden Smith"],"actor":["A","B"],
                |"category":[{"name":"Khoa Học","slug":"khoa-hoc"}],"country":[{"name":"Hàn Quốc","slug":"han-quoc"}],
                |"modified":{"time":"2026-09-20T00:00:00.000Z"},"view":900,"episodes":[]}""".trimMargin()
        }
    }
}