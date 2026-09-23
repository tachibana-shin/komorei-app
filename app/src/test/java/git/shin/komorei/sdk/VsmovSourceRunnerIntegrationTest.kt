package git.shin.komorei.sdk

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.runner.Anime
import git.shin.komorei.sdk.runner.AnimeStatus
import git.shin.komorei.sdk.runner.DeepLinkResult
import git.shin.komorei.sdk.runner.FilterKind
import git.shin.komorei.sdk.runner.FilterValue
import git.shin.komorei.sdk.runner.HomeComponentValue
import git.shin.komorei.sdk.runner.HostDefaultValue
import git.shin.komorei.sdk.runner.LinkValue
import git.shin.komorei.sdk.runner.ListingKind
import git.shin.komorei.sdk.runner.SettingValue
import git.shin.komorei.sdk.runner.StreamType
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
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The REAL VSMov source (`sources/sources/vi.vsmov` → `package.krx`) driven
 * through the real runner (uniffi bindings + linux cdylib) and the real Kotlin
 * host ([KrxHostImpl]), exactly like [OphimSourceRunnerIntegrationTest] but
 * against the *vsmov* flat-fork API:
 *
 *  - list/search/detail payloads sit at the **root** of the envelope
 *    (`items` / `movie` + `episodes`) instead of under `data`,
 *  - detail episodes expose **only `link_embed`** (`https://v<host>
 *    .streamvsmov.com/video/<hash>`) — the source derives the HLS master
 *    playlist (`…/stream/<hash>/master.m3u8`) by string transform and fetches
 *    the embed page to extract `playerOptions.subtitles` (real `.vtt` tracks),
 *  - `server_name` labels carry trailing `\r\n #N` noise that must be folded
 *    to clean "Vietsub #1" season keys,
 *  - the +07:00 (`-HH:MM`) wall-clock dates in `modified.time` must parse into
 *    correct UTC epochs.
 *
 * The fixture's `link_embed` points at the fixture HTTP server itself
 * (`{fixture}/video/<hash>`), so the embed fetch, the derived playlist URL and
 * the absolutized `.vtt` subtitle URLs all stay hermetic (no real
 * streamvsmov.com traffic).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VsmovSourceRunnerIntegrationTest {

    private lateinit var host: KrxHostImpl
    private lateinit var runner: git.shin.komorei.sdk.runner.KomoreiRunner
    private lateinit var fixture: FixtureServer

    companion object {
        private val vsmovKrx: String = System.getProperty("komorei.test.vsmovKrx")
            ?: error("missing -Dkomorei.test.vsmovKrx (set by app/build.gradle.kts)")
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
        // The source's default is https://vsmov.com/api; point it at the fixture.
        host.defaultsSet("base_url", HostDefaultValue.String(fixture.baseUrl + "/api"))
        runner = KrxManager.load(host, File(vsmovKrx).readBytes())
    }

    // ── home & listings ─────────────────────────────────────────────────────

    @Test
    fun `home assembles the three vsmov rails`() {
        val home = runner.home()
        assertEquals(
            setOf(
                "/api/danh-sach/phim-moi-cap-nhat?page=1",
                "/api/danh-sach/phim-bo?page=1",
                "/api/danh-sach/phim-le?page=1",
            ),
            fixture.requests.toSet(),
        )
        assertEquals(
            listOf("Nổi Bật", "Mới Cập Nhật", "Phim Bộ", "Phim Lẻ", "Thể Loại", "Danh Sách"),
            home.components.map { it.title.orEmpty() },
        )
        val promo = home.components[0].value as HomeComponentValue.ImageScroller
        val first = (promo.links[0].value as LinkValue.Anime).v1
        assertEquals("nhat-au-xuan", first.key)
        assertEquals("${fixture.baseUrl}/posters/nhat-au-xuan.jpg", first.cover)

        // Recent-updates rail rides the lite list: real +07:00 date, stub ep.
        val latest = home.components[1].value as HomeComponentValue.AnimeEpisodeList
        val entry = latest.entries[0]
        assertEquals("nhat-au-xuan", entry.anime.key)
        assertEquals("nhat-au-xuan__latest", entry.episode.key)
        // 2026-09-21T00:19:37+07:00 → UTC epoch (7 h earlier).
        assertEquals(1_789_924_777_000L, entry.episode.dateUploaded)
        // Lite vsmov list items carry no episode info — the stub falls back to "1".
        assertEquals("1", entry.episode.episodeNumber)
    }

    @Test
    fun `listings expose the five vsmov rails and paginate`() {
        val listings = runner.listings()
        assertEquals(
            listOf("latest", "bo", "le", "chieu-rap", "subteam"),
            listings.map { it.id },
        )
        assertTrue(listings.all { it.kind == ListingKind.LIST })

        val page1 = runner.animeList(listings.first(), 1)
        assertEquals("/api/danh-sach/phim-moi-cap-nhat?page=1", fixture.lastRequest())
        assertEquals(2, page1.entries.size)
        assertTrue(page1.hasNextPage) // pagination.currentPage < totalPages

        val page2 = runner.animeList(listings.first(), 2)
        assertTrue(page2.entries.isEmpty())
        assertFalse(page2.hasNextPage)
    }

    // ── search & filters ────────────────────────────────────────────────────

    @Test
    fun `keyword search hits the flat tim-kiem endpoint`() {
        val result = runner.search("bao thanh", 1, emptyList())
        // QueryParameters percent-encodes: the space becomes %20.
        assertEquals("/api/tim-kiem?page=1&keyword=bao%20thanh", fixture.lastRequest())
        assertFalse(result.hasNextPage)
        assertEquals(1, result.entries.size)
        val hit = result.entries.single()
        assertEquals("nhat-au-xuan", hit.key)
        assertEquals("Nhất Âu Xuân", hit.title)
        assertEquals("Once Upon a Time", hit.originalTitle)
        assertEquals("${fixture.baseUrl}/posters/nhat-au-xuan.jpg", hit.cover)
        assertEquals("2026", hit.releaseYear!!.name)
    }

    @Test
    fun `genre chip routes to the flat recently-updated list with the slug`() {
        val result = runner.search(
            null, 2,
            listOf(FilterValue.MultiSelect("category", listOf("Hành Động"), emptyList())),
        )
        // No keyword → the empty-keyword-proof fallback list, with the genre
        // name mapped to its vsmov slug.
        assertEquals("/api/danh-sach/phim-moi-cap-nhat?page=2&category=hanh-dong", fixture.lastRequest())
        assertEquals(2, result.entries.size)
    }

    // ── anime update & chapters ─────────────────────────────────────────────

    @Test
    fun `anime update fills details status and the two servers`() {
        val lite = runner.search(null, 1, emptyList()).entries.single { it.key == "nhat-au-xuan" }
        // This is a LITE list item: title only, no seasons yet.
        assertTrue(lite.seasons.isEmpty())

        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = false)

        assertEquals("Nhất Âu Xuân", full.title)
        assertEquals("Once Upon a Time", full.originalTitle)
        assertTrue(full.description.orEmpty().startsWith("Nhất Âu Xuân"))
        assertEquals(AnimeStatus.ONGOING, full.status)
        assertEquals("HD", full.qualityTag)
        assertEquals("Tập 16", full.currentEpisode)
        assertEquals("2026", full.releaseYear!!.name)
        assertEquals(listOf("Hành Động"), full.genres.map { it.name })
        assertEquals(listOf("Trung Quốc"), full.countries.map { it.name })
        assertEquals(120, full.views)
        // episode count = largest playback server (Vietsub has tap-1 + tap-2).
        assertEquals(2, full.episodeCount)

        // server_name "Vietsub\r\n  #1" folded to a clean "Vietsub #1" key.
        assertEquals(
            listOf("nhat-au-xuan|Vietsub #1", "nhat-au-xuan|Trailer"),
            full.seasons.map { it.animeId },
        )
    }

    @Test
    fun `chapters come from the largest playback server`() {
        val full = runner.animeUpdate(
            runner.search(null, 1, emptyList()).entries.single { it.key == "nhat-au-xuan" },
            needsDetails = true, needsChapters = true,
        )
        val eps = full.episodes!!
        assertEquals(listOf("tap-1", "tap-2"), eps.map { it.key })
        assertEquals(listOf("1", "2"), eps.map { it.episodeNumber })
        assertEquals("vi", eps.first().language)
    }

    // ── streams ─────────────────────────────────────────────────────────────

    @Test
    fun `stream derives the master playlist and pulls the vtt subtitles from the embed page`() {
        val full = runner.animeUpdate(
            runner.search(null, 1, emptyList()).entries.single { it.key == "nhat-au-xuan" },
            needsDetails = true, needsChapters = true,
        )
        val ep = full.episodes!!.last() // tap-2
        val servers = runner.streamList(full, ep)
        assertEquals(listOf("Vietsub #1", "Trailer"), servers.map { it.key })

        val data = runner.stream(full, ep, servers.first())

        // link_embed → …/stream/<hash>/master.m3u8 on the same host (the
        // runner's transform hardcodes the https scheme).
        assertEquals(
            "${fixture.httpsOrigin}/stream/6a002aba-05c7-4ce3-a89a-0b214eac60f8/master.m3u8",
            data.url,
        )
        assertEquals(StreamType.HLS, data.streamType)
        assertEquals("${fixture.httpsOrigin}/", data.headers["Referer"])

        // The embed page fetch happened and its playerOptions subtitles came back.
        assertTrue(
            "embed page fetched",
            fixture.requests.contains("/video/6a002aba-05c7-4ce3-a89a-0b214eac60f8"),
        )
        assertEquals(2, data.subtitles.size)
        val vie = data.subtitles[0]
        assertEquals(
            "${fixture.httpsOrigin}/video/6a002aba-05c7-4ce3-a89a-0b214eac60f8/subtitle/vie_1789619837502_zzigvu.vtt",
            vie.url,
        )
        assertEquals("vi", vie.language)
        assertEquals("vie", vie.label)
        val eng = data.subtitles[1]
        assertTrue(eng.url.endsWith("subtitle/eng_1789619837451_zzigvu.vtt"))
        assertEquals("en", eng.language)
        assertEquals("eng", eng.label)
    }

    @Test
    fun `m3u8 link_embed passes through untouched`() {
        // A fixture variant whose link_embed is already a playlist URL.
        fixture.directPlaylist = true
        val full = runner.animeUpdate(
            runner.search(null, 1, emptyList()).entries.single { it.key == "nhat-au-xuan" },
            needsDetails = true, needsChapters = true,
        )
        val ep = full.episodes!!.last()
        val data = runner.stream(full, ep, runner.streamList(full, ep).first())
        // Punished by fixture serving a direct master.m3u8 for tap-2.
        assertEquals(
            "${fixture.baseUrl}/stream/6a002aba-05c7-4ce3-a89a-0b214eac60f8/master.m3u8",
            data.url,
        )
    }

    // ── settings / deep link / migration ────────────────────────────────────

    @Test
    fun `settings default to the vsmov api and expose the full filter catalogs`() {
        val byKey = runner.settings().associateBy { it.key }
        val baseUrl = byKey.getValue("base_url")
        assertEquals("Địa chỉ API VSMov", baseUrl.title)
        assertEquals(listOf("content", "listings"), baseUrl.refreshes)
        val text = baseUrl.value as SettingValue.Text
        assertEquals("https://vsmov.com/api", text.default)
        assertEquals("https://vsmov.com/api", text.placeholder)
        assertTrue(byKey.containsKey("clear_cache"))

        val filters = runner.filters().associateBy { it.id }
        val category = filters.getValue("category").kind as FilterKind.MultiSelect
        assertTrue(category.isGenre)
        assertTrue(category.usesTagStyle)
        assertTrue(category.options.contains("Hành Động"))
        assertTrue(category.options.contains("Kinh Dị"))
        val country = filters.getValue("country").kind as FilterKind.MultiSelect
        assertFalse(country.isGenre)
        assertTrue(country.options.contains("Hàn Quốc"))
        assertTrue(country.options.contains("Trung Quốc"))
        assertTrue(filters.getValue("type").kind is FilterKind.Select)
        val year = filters.getValue("year").kind as FilterKind.Range
        assertEquals(1980f, year.min!!, 0.001f)
    }

    @Test
    fun `deep link resolves detail paths and migration keeps identity`() {
        val detail = runner.deepLink("https://vsmov.com/phim/nhat-au-xuan/")
        assertTrue(detail is DeepLinkResult.Anime)
        assertEquals("nhat-au-xuan", (detail as DeepLinkResult.Anime).key)

        assertNull(runner.deepLink("https://vsmov.com/"))
        assertNull(runner.deepLink(""))

        assertEquals("nhat-au-xuan", runner.migrateAnime("nhat-au-xuan"))
        assertEquals("tap-2", runner.migrateEpisode("nhat-au-xuan", "tap-2"))
    }

    // ── fixture server (speaks the flat vsmov.com API protocol) ─────────────

    private class FixtureServer : Closeable {
        private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        @Volatile private var closed = false
        val requests = CopyOnWriteArrayList<String>()

        /** The fixture's real listen origin (`http://127.0.0.1:<port>`). */
        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"
        /** The origin the source's URL transforms produce (`https://` is hardcoded). */
        val httpsOrigin: String get() = "https://127.0.0.1:${server.localPort}"

        /** When set, tap-2's link_embed is already a master.m3u8 (pass-through test). */
        @Volatile var directPlaylist = false

        init {
            Thread({ acceptLoop() }, "vsmov-fixture").apply { isDaemon = true; start() }
        }

        fun lastRequest(): String = requests.lastOrNull() ?: ""

        private fun acceptLoop() {
            while (!closed) {
                val sock = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                Thread({ handle(sock) }, "vsmov-fixture-handler").apply { isDaemon = true; start() }
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
                        if (idx > 0) {
                            headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                        }
                        line = input.readLine()
                    }

                    requests += target

                    val (status, contentType, body) = route(target)
                    respond(s, status, mapOf("Content-Type" to "$contentType; charset=utf-8"), body)
                }
            } catch (e: Exception) {
                // fixture: swallow per-connection errors
            }
        }

        private fun respond(sock: Socket, status: Int, headers: Map<String, String>, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val hdrs = buildString {
                append("HTTP/1.1 $status OK\r\n")
                headers.forEach { (k, v) -> append("$k: $v\r\n") }
                append("Content-Length: ${bytes.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            val out = sock.getOutputStream()
            out.write(hdrs.toByteArray(Charsets.US_ASCII))
            if (bytes.isNotEmpty()) out.write(bytes)
            out.flush()
        }

        private fun route(target: String): Triple<Int, String, String> {
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")
            return when {
                path == "/" -> Triple(200, "text/html", "<html><body>ok</body></html>")
                path == "/api/tim-kiem" -> Triple(200, "application/json", searchEnvelope)
                path == "/api/danh-sach/phim-moi-cap-nhat" ->
                    when {
                        // genre/country chips arrive as page=2&category=… — real content
                        query.contains("category=") || query.contains("country=") ->
                            Triple(200, "application/json", latestPage)
                        query.contains("page=2") -> Triple(200, "application/json", emptyPage)
                        else -> Triple(200, "application/json", latestPage)
                    }
                path == "/api/danh-sach/phim-bo" -> Triple(200, "application/json", boPage)
                path == "/api/danh-sach/phim-le" -> Triple(200, "application/json", lePage)
                path.startsWith("/api/danh-sach/") -> Triple(200, "application/json", boPage)
                path == "/api/phim/nhat-au-xuan" -> Triple(200, "application/json", detailJson)
                path.startsWith("/api/phim/") -> Triple(200, "application/json", phapDetailJson)
                path.startsWith("/video/") ->
                    Triple(200, "text/html", embedHtml(path.substringAfter("/video/")))
                else -> Triple(404, "application/json", """{"status":false,"msg":"not found"}""")
            }
        }

        // ── lite list items (mirror the live keys: _id, imdb, modified, name,
        //    origin_name, poster_url, slug, thumb_url, tmdb, year) ─────────────

        private fun liteItem(
            slug: String,
            name: String,
            origin: String,
            year: Int,
            time: String,
        ) = """
            {
              "_id": ${slug.hashCode().toLong().and(0x7fffffff)},
              "name": "$name",
              "origin_name": "$origin",
              "slug": "$slug",
              "poster_url": "$baseUrl/posters/$slug.jpg",
              "thumb_url": "$baseUrl/posters/$slug-thumb.jpg",
              "year": $year,
              "modified": { "time": "$time" },
              "tmdb": { "id": null },
              "imdb": { "id": null }
            }
        """.trimIndent()

        private val nhatLite = liteItem("nhat-au-xuan", "Nhất Âu Xuân", "Once Upon a Time", 2026, "2026-09-21T00:19:37+07:00")
        private val phapLite = liteItem("phap-y", "Pháp Y", "Forensic", 2022, "2026-09-20T19:12:05+07:00")

        // ── flat list envelopes ───────────────────────────────────────────────

        private val latestPage = """
            {"status": true, "msg": "done",
             "items": [$nhatLite, $phapLite],
             "pagination": {"totalItems": 48, "totalItemsPerPage": 24, "currentPage": 1, "totalPages": 2}}
        """.trimIndent()

        private val boPage = """
            {"status": true, "msg": "done",
             "items": [$nhatLite],
             "pagination": {"totalItems": 24, "totalItemsPerPage": 24, "currentPage": 1, "totalPages": 1}}
        """.trimIndent()

        private val lePage = """
            {"status": true, "msg": "done",
             "items": [$phapLite],
             "pagination": {"totalItems": 24, "totalItemsPerPage": 24, "currentPage": 1, "totalPages": 1}}
        """.trimIndent()

        private val emptyPage = """
            {"status": true, "msg": "done",
             "items": [],
             "pagination": {"totalItems": 48, "totalItemsPerPage": 24, "currentPage": 2, "totalPages": 2}}
        """.trimIndent()

        private val searchEnvelope = """
            {"status": true, "msg": "done",
             "items": [$nhatLite],
             "pagination": {"totalItems": 13, "totalItemsPerPage": 24, "currentPage": 1, "totalPages": 1}}
        """.trimIndent()

        // ── detail (root `movie` + root `episodes`, vsmov's flat fork) ────────

        private val detailJson: String by lazy {
            val ep2 = if (directPlaylist) {
                """{"name":"2","slug":"tap-2","filename":"2","link_embed":"$baseUrl/stream/6a002aba-05c7-4ce3-a89a-0b214eac60f8/master.m3u8"}"""
            } else {
                """{"name":"2","slug":"tap-2","filename":"2","link_embed":"$baseUrl/video/6a002aba-05c7-4ce3-a89a-0b214eac60f8"}"""
            }
            """
            {
              "status": true,
              "msg": "done",
              "movie": {
                "_id": 52762,
                "name": "Nhất Âu Xuân",
                "origin_name": "Once Upon a Time",
                "slug": "nhat-au-xuan",
                "poster_url": "$baseUrl/posters/nhat-au-xuan.jpg",
                "thumb_url": "$baseUrl/posters/nhat-au-xuan-thumb.jpg",
                "year": 2026,
                "type": "series",
                "quality": "HD",
                "lang": "Vietsub",
                "episode_total": "30",
                "episode_current": "Tập 16",
                "status": "ongoing",
                "content": "<p>Nhất Âu Xuân theo chân Triệu Thụy phá án liên hoàn...<\/p>",
                "director": ["Phạm Công"],
                "actor": ["Ngô Lỗi", "Hứa Khải"],
                "category": [{ "name": "Hành Động", "slug": "hanh-dong" }],
                "country": [{ "name": "Trung Quốc", "slug": "trung-quoc" }],
                "modified": { "time": "2026-09-21T00:19:37+07:00" },
                "view": 120,
                "tmdb": { "type": "tv", "id": "287994" }
              },
              "episodes": [
                { "server_name": "Vietsub\r\n  #1", "server_data": [
                  { "name": "1", "slug": "tap-1", "filename": "1",
                    "link_embed": "$baseUrl/video/6a002aba-05c7-4ce3-a89a-0b214eac60f8" },
                  $ep2
                ]},
                { "server_name": "Trailer", "server_data": [
                  { "name": "1", "slug": "trailer-1", "filename": "1",
                    "link_embed": "$baseUrl/video/9f88d3bb-1111-4bbb-cccc-000000000001" }
                ]}
              ]
            }
            """.trimIndent()
        }

        private val phapDetailJson = """
            {"status": true, "msg": "done",
             "movie": {
               "_id": 45232,
               "name": "Pháp Y",
               "origin_name": "Forensic",
               "slug": "phap-y",
               "poster_url": "$baseUrl/posters/phap-y.jpg",
               "thumb_url": "$baseUrl/posters/phap-y-thumb.jpg",
               "year": 2022,
               "type": "series",
               "quality": "FHD",
               "lang": "Vietsub",
               "episode_total": "36",
               "episode_current": "Tập 36",
               "status": "completed",
               "content": "<p>Đội pháp y Cục công an...<\/p>",
               "category": [{ "name": "Hình Sự", "slug": "hinh-su" }],
               "country": [{ "name": "Trung Quốc", "slug": "trung-quoc" }]
             },
             "episodes": [
               { "server_name": "Vietsub\r\n  #1", "server_data": [
                 { "name": "1", "slug": "tap-1", "filename": "1",
                   "link_embed": "$baseUrl/video/aaaa0000-0000-4000-8000-000000000001" }
               ]}
             ]}
        """.trimIndent()

        // ── embed page (playerOptions.subtitles with relative .vtt paths) ─────

        private fun embedHtml(hash: String): String = """
            <!DOCTYPE html><html><head><meta charset="utf-8"></head><body>
            <script>
              var playerOptions = {
                "qualitys": [],
                "links": [],
                "subtitles": [
                  {"name":"vie 1789619837502 zzigvu","type":"local","url":"/video/$hash/subtitle/vie_1789619837502_zzigvu.vtt","_inSubtitleFolder":true,"code":"vie"},
                  {"name":"eng 1789619837451 zzigvu","type":"local","url":"/video/$hash/subtitle/eng_1789619837451_zzigvu.vtt","_inSubtitleFolder":true,"code":"eng"}
                ],
                "thumb": "javascript:void(0)"
              };
            </script>
            </body></html>
        """.trimIndent()

        override fun close() {
            closed = true
            try {
                server.close()
            } catch (_: IOException) {
            }
        }
    }
}