package git.shin.komorei.sdk

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.runner.Anime
import git.shin.komorei.sdk.runner.AnimeStatus
import git.shin.komorei.sdk.runner.DeepLinkResult
import git.shin.komorei.sdk.runner.FilterKind
import git.shin.komorei.sdk.runner.FilterValue
import git.shin.komorei.sdk.runner.HostDefaultValue
import git.shin.komorei.sdk.runner.LinkValue
import git.shin.komorei.sdk.runner.ListingKind
import git.shin.komorei.sdk.runner.SettingValue
import git.shin.komorei.sdk.runner.StreamType
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
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
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The REAL Nguồn Phim site source (`sources/sources/vi.nguonphime` →
 * `package.krx`) driven through the real runner (uniffi bindings + linux
 * cdylib) and the real Kotlin host ([KrxHostImpl]), exactly like
 * [NguonphimSourceRunnerIntegrationTest] but for the *HTML* side of the same
 * network: an HTML scrape of nguonphime.site with
 *
 *  - the **NP Checker bounce** — the first cookie-less request of a session is
 *    302-redirected to `/site/site/embed/?url=…`, whose response sets
 *    `PHPSESSID` (stored by the cookie jar) and then serves the interstitial
 *    page. The source detects the checker body and retries once. To mirror the
 *    app's `WebViewCookieJar`, the host is constructed with an OkHttp client
 *    carrying a [JavaNetCookieJar] so the `Set-Cookie` round-trips.
 *  - the **PAI flow** — watch-page XHR POST → signed grab iframe page → base64
 *    playlist (`JSON.parse(atob(…))`) decoded natively.
 *  - the **NGC flow** — the grab page's own `var url = '…fromEmbed=1…'` POST →
 *    `embed.php?hash=` iframe → the shared streamc bootstrap → issue grant.
 *
 * The krx is located via `-Dkomorei.test.nguonphimeKrx` (set by
 * app/build.gradle.kts) and `base_url` is pointed at a local `ServerSocket`
 * fixture speaking exactly the site's HTML.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NguonphimeSourceRunnerIntegrationTest {

    private lateinit var host: KrxHostImpl
    private lateinit var runner: git.shin.komorei.sdk.runner.KomoreiRunner
    private lateinit var fixture: FixtureServer

    companion object {
        private val nguonphimeKrx: String = System.getProperty("komorei.test.nguonphimeKrx")
            ?: error("missing -Dkomorei.test.nguonphimeKrx (set by app/build.gradle.kts)")
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
        // The app's production client rides WebViewCookieJar; this in-memory jar
        // plays that role so the NP Checker Set-Cookie survives into the source's
        // retry request (faithful end-to-end bounce). OkHttp 5 removed the
        // bundled JavaNetCookieJar, hence the small local CookieJar.
        val client = OkHttpClient.Builder()
            .cookieJar(MemoryCookieJar())
            .build()
        host = KrxHostImpl(context, okHttpClient = client)
        host.defaultsSet("base_url", HostDefaultValue.String(fixture.baseUrl))
        runner = KrxManager.load(host, File(nguonphimeKrx).readBytes())
    }

    // ── home & listings ─────────────────────────────────────────────────────

    @Test
    fun `home assembles the rails after absorbing the checker bounce`() {
        val home = runner.home()
        assertEquals(
            setOf(
                "/tuy-chon/phim-moi.html?ft=ne&ne=1&page=1",
                "/tuy-chon/phim-bo.html?ft=ty&ty=2&page=1",
                "/tuy-chon/phim-le.html?ft=ty&ty=1&page=1",
            ),
            fixture.requests.filter { !it.startsWith("/site/site/embed/") }.toSet(),
        )
        // The first cookie-less request bounced through the NP Checker once.
        assertTrue("session bounced through NP Checker", fixture.bounces > 0)
        assertEquals(
            listOf("Nổi Bật", "Mới Cập Nhật", "Phim Bộ", "Phim Lẻ", "Thể Loại", "Danh Sách"),
            home.components.map { it.title.orEmpty() },
        )
        val promo = home.components[0].value as git.shin.komorei.sdk.runner.HomeComponentValue.ImageScroller
        assertTrue(promo.links[0].value is LinkValue.Anime)
        val first = (promo.links[0].value as LinkValue.Anime).v1
        assertEquals("lan-huong-nhu-co-against-the-current-f83892", first.key)
    }

    @Test
    fun `listings expose the site rail paths and paginate through the Pager`() {
        val listings = runner.listings()
        assertEquals(
            listOf(
                "tuy-chon/phim-moi.html?ft=ne&ne=1",
                "tuy-chon/phim-hot.html?ft=ho&ho=1",
                "tuy-chon/phim-bo.html?ft=ty&ty=2",
                "tuy-chon/phim-le.html?ft=ty&ty=1",
                "phim-chieu-rap-c18.html",
                "phim-hoat-hinh-c7.html",
                "phim-hanh-dong-c3.html",
                "phim-co-trang-c14.html",
            ),
            listings.map { it.id },
        )
        assertTrue(listings.all { it.kind == ListingKind.LIST })

        val page1 = runner.animeList(listings.first(), 1)
        assertEquals("/tuy-chon/phim-moi.html?ft=ne&ne=1&page=1", fixture.lastRequest())
        assertEquals(2, page1.entries.size)
        assertTrue(page1.hasNextPage) // Pager links past page 1

        val page2 = runner.animeList(listings.first(), 2)
        assertTrue(page2.entries.isEmpty())
        assertFalse(page2.hasNextPage)
    }

    // ── search & filters ────────────────────────────────────────────────────

    @Test
    fun `keyword search posts the live-search form and parses the dropdown`() {
        val result = runner.search("Lan Hương", 1, emptyList())
        assertEquals("/tim-kiem-a.html", fixture.lastRequest())
        assertFalse(result.hasNextPage)
        assertEquals(1, result.entries.size)
        val hit = result.entries.single()
        assertEquals("lan-huong-nhu-co-against-the-current-f83892", hit.key)
        assertEquals("Lan Hương Như Cố", hit.title)
        assertEquals("Against The Current", hit.originalTitle)
        assertTrue(hit.cover.contains("lan-huong.jpg"))
    }

    @Test
    fun `genre filter routes to a category page`() {
        val result = runner.search(
            null, 2,
            listOf(FilterValue.MultiSelect("genre", listOf("Kinh Dị"), emptyList())),
        )
        assertEquals("/phim-kinh-di-c9.html?page=2", fixture.lastRequest())
        assertEquals(1, result.entries.size)
        assertEquals("kim-loai-f55555", result.entries.single().key)
    }

    // ── anime update & chapters ─────────────────────────────────────────────

    @Test
    fun `anime update fills details status and the two servers`() {
        val lite = runner.search(null, 1, emptyList()).entries.single {
            it.key == "lan-huong-nhu-co-against-the-current-f83892"
        }
        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = false)

        assertEquals("Lan Hương Như Cố", full.title)
        assertEquals("Against The Current", full.originalTitle)
        assertTrue(full.description.orEmpty().startsWith("Thẩm Gia Lan"))
        assertEquals(8.5f, full.rating!!, 0.001f)
        assertEquals(AnimeStatus.ONGOING, full.status)
        assertEquals(listOf("Phim Tâm Lý - Tình Cảm", "Phim Cổ Trang"), full.genres.map { it.name })
        assertEquals(listOf("Trung Quốc"), full.countries.map { it.name })
        assertEquals("2026", full.releaseYear!!.name)
        assertEquals("Tập 24", full.currentEpisode)
        assertEquals(47, full.episodeCount)

        assertEquals(listOf("PAI", "NGC"), full.seasons.map { it.animeId.substringAfter('|') })
        val seasons = full.seasons.map { it.animeId }
        assertTrue(seasons.all { it.startsWith("lan-huong-nhu-co-against-the-current-f83892|") })
    }

    @Test
    fun `chapters come from the watch page with playable urls`() {
        val full = runner.animeUpdate(
            runner.search(null, 1, emptyList()).entries.single {
                it.key == "lan-huong-nhu-co-against-the-current-f83892"
            },
            needsDetails = false, needsChapters = true,
        )
        val eps = full.episodes!!
        assertEquals(listOf("1-e1006847", "2-e1006848", "24-e1007951"), eps.map { it.key })
        assertEquals(listOf("1", "2", "24"), eps.map { it.episodeNumber })
        assertEquals(
            "${fixture.baseUrl}/xem-phim/lan-huong-nhu-co-against-the-current-f83892-24-e1007951.html",
            eps.last().url,
        )
        assertEquals("vi", eps.first().language)
    }

    // ── streams ─────────────────────────────────────────────────────────────

    @Test
    fun `PAI stream decodes the base64 playlist from the grab page`() {
        val full = updateWithChapters()
        val ep24 = full.episodes!!.last()
        val servers = runner.streamList(full, ep24)
        assertEquals(listOf("PAI", "NGC"), servers.map { it.key })

        val data = runner.stream(full, ep24, servers.first())
        assertEquals("${fixture.baseUrl}/pai/master.m3u8", data.url)
        assertEquals(StreamType.HLS, data.streamType)
        assertNull(data.headers["Referer"])
        assertNull(data.headers["token"])
    }

    @Test
    fun `NGC stream walks the fromEmbed grant and carries the embed referer`() {
        val full = updateWithChapters()
        val ep24 = full.episodes!!.last()
        val ngc = runner.streamList(full, ep24).first { it.key == "NGC" }
        val data = runner.stream(full, ep24, ngc)

        val embedPosts = fixture.posts.filter { it.target.contains("embed.php") }
        assertEquals(2, embedPosts.size)
        assertEquals("bootstrap", embedPosts[0].action)
        assertEquals("issue", embedPosts[1].action)
        assertEquals(fixture.baseUrl, embedPosts[0].origin)

        assertEquals("${fixture.baseUrl}/signed/abc123.m3u8", data.url)
        assertEquals(StreamType.HLS, data.streamType)
        assertEquals("${fixture.baseUrl}/", data.headers["Referer"])
    }

    // ── settings / deep link / migration ────────────────────────────────────

    @Test
    fun `settings default to the site url and expose the catalogs`() {
        val byKey = runner.settings().associateBy { it.key }
        val baseUrl = byKey.getValue("base_url")
        assertEquals("Địa chỉ trang Nguồn Phim", baseUrl.title)
        assertEquals(listOf("content", "listings"), baseUrl.refreshes)
        val text = baseUrl.value as SettingValue.Text
        assertEquals("https://nguonphime.site", text.default)
        assertEquals("https://nguonphime.site", text.placeholder)

        val genre = runner.filters().associateBy { it.id }.getValue("genre").kind as FilterKind.MultiSelect
        assertTrue(genre.isGenre)
        assertTrue(genre.options.contains("Kinh Dị"))
        assertTrue(genre.options.contains("Hành Động"))
        val country = runner.filters().associateBy { it.id }.getValue("country").kind as FilterKind.MultiSelect
        assertTrue(country.options.contains("Hàn Quốc"))
        assertTrue(country.options.contains("Mỹ"))
    }

    @Test
    fun `deep link resolves watch and detail paths, migration keeps identity`() {
        val anim = runner.deepLink(
            "${fixture.baseUrl}/xem-phim/lan-huong-nhu-co-against-the-current-f83892-24-e1007951.html",
        )
        assertTrue(anim is DeepLinkResult.Anime)
        assertEquals("lan-huong-nhu-co-against-the-current-f83892", (anim as DeepLinkResult.Anime).key)

        val plain = runner.deepLink("${fixture.baseUrl}/kim-loai-f55555.html")
        assertEquals("kim-loai-f55555", (plain as DeepLinkResult.Anime).key)

        assertEquals(
            "lan-huong-nhu-co-against-the-current-f83892",
            runner.migrateAnime("lan-huong-nhu-co-against-the-current-f83892"),
        )
        assertEquals("24-e1007951", runner.migrateEpisode("lan-huong-nhu-co-against-the-current-f83892", "24-e1007951"))
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun updateWithChapters(): Anime = runner.animeUpdate(
        runner.search(null, 1, emptyList()).entries.single {
            it.key == "lan-huong-nhu-co-against-the-current-f83892"
        },
        needsDetails = true, needsChapters = true,
    )

    // ── fixture server (speaks the nguonphime.site HTML protocol) ───────────

    private class FixtureServer : Closeable {
        private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        @Volatile private var closed = false
        val requests = CopyOnWriteArrayList<String>()
        val posts = CopyOnWriteArrayList<Post>()
        @Volatile var bounces = 0

        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

        class Post(val target: String, val action: String, val origin: String?, val referer: String?)

        init {
            Thread({ acceptLoop() }, "nguonphime-fixture").apply { isDaemon = true; start() }
        }

        fun lastRequest(): String = requests.lastOrNull() ?: ""

        private fun acceptLoop() {
            while (!closed) {
                val sock = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                Thread({ handle(sock) }, "nguonphime-fixture-handler").apply { isDaemon = true; start() }
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
                            body.contains("indexL=1") && body.contains("fid=") -> "ngc"
                            body.contains("fid=") -> "pai"
                            body.contains("q=") -> "search"
                            else -> "unknown"
                        }
                        posts += Post(target, action ?: "unknown", headers["origin"], headers["referer"])
                    }
                    requests += target

                    // Checker bounce: streamc's embed.php is a separate product
                    // (no bounce); /site/site/embed is the interstitial itself.
                    val hasSession = headers["cookie"]?.contains("PHPSESSID") == true
                    if (!target.startsWith("/embed.php") && !hasSession) {
                        if (target.startsWith("/site/site/embed/")) {
                            bounces++
                            respond(s, 200, mapOf("Content-Type" to "text/html; charset=utf-8"), checkerHtml)
                            return
                        }
                        bounces++
                        respond(
                            s, 302,
                            mapOf(
                                "Location" to "/site/site/embed/?url=${target}",
                                "Set-Cookie" to "PHPSESSID=fixture-session; path=/; httponly",
                            ),
                            "",
                        )
                        return
                    }
                    if (target.startsWith("/site/site/embed/")) {
                        respond(s, 200, mapOf("Content-Type" to "text/html; charset=utf-8"), checkerHtml)
                        return
                    }

                    val (status, contentType, body) = route(target, method, action)
                    respond(s, status, mapOf("Content-Type" to "$contentType; charset=utf-8"), body)
                }
            } catch (e: Exception) {
                // fixture: swallow per-connection errors
            }
        }

        private fun respond(sock: Socket, status: Int, headers: Map<String, String>, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val reason = if (status == 302) "Found" else "OK"
            val hdrs = buildString {
                append("HTTP/1.1 $status $reason\r\n")
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

        private fun route(target: String, method: String, action: String?): Triple<Int, String, String> {
            val path = target.substringBefore('?')
            return when {
                method == "POST" && path == "/tim-kiem-a.html" -> Triple(200, "application/json", searchJson)
                method == "POST" && path.contains("/xem-phim/") -> when (action) {
                    "ngc" -> Triple(200, "application/json", ngcIframeJson)
                    else -> Triple(200, "application/json", paiIframeJson)
                }
                method == "POST" && path.contains("/embed.php") -> when (action) {
                    "issue" -> Triple(200, "application/json", issueJson(target))
                    else -> Triple(200, "application/json", bootstrapJson(target))
                }
                path == "/" -> Triple(200, "text/html", "<html><body>warmup</body></html>")
                path == "/tuy-chon/phim-moi.html" ->
                    if (target.contains("page=2")) Triple(200, "text/html", listPage("", pager2))
                    else Triple(200, "text/html", listPage(cardA + cardB, pager1))
                path == "/tuy-chon/phim-bo.html" -> Triple(200, "text/html", listPage(cardA, pagerOnly1))
                path == "/tuy-chon/phim-le.html" -> Triple(200, "text/html", listPage(cardB, pagerOnly1))
                path == "/phim-kinh-di-c9.html" -> Triple(200, "text/html", listPage(cardB, pagerOnly1))
                path == "/tuy-chon/han-quoc.html" -> Triple(200, "text/html", listPage(cardA, pagerOnly1))
                path == "/tuy-chon/2026.html" -> Triple(200, "text/html", listPage(cardA, pagerOnly1))
                path == "/lan-huong-nhu-co-against-the-current-f83892.html" -> Triple(200, "text/html", detailHtml)
                path == "/xem-phim/lan-huong-nhu-co-against-the-current-f83892.html" -> Triple(200, "text/html", watchHtml)
                path.startsWith("/grab/") -> Triple(200, "text/html", grabHtml)
                else -> Triple(200, "text/html", "<html><body>unknown</body></html>")
            }
        }

        // ── page fragments ──────────────────────────────────────────────────

        private fun listPage(cards: String, pager: String) =
            "<!DOCTYPE html><html><body><div class=\"grid-movie\">$cards</div>$pager</body></html>"

        private val pager1 =
            """<ul id="yw0" class="Pager"><li class="page"><a href="?page=1">1</a></li><li class="page"><a href="?page=2">2</a></li></ul>"""
        private val pager2 =
            """<ul id="yw0" class="Pager"><li class="page"><a href="?page=2">2</a></li></ul>"""
        private val pagerOnly1 =
            """<ul id="yw0" class="Pager"><li class="page"><a href="?page=1">1</a></li></ul>"""

        private val cardA = """
            <div class="item-file-index border-item clearfix">
              <div class="img-item-file-index">
                <a href="/lan-huong-nhu-co-against-the-current-f83892.html" title="Lan Hương Như Cố">
                  <img class="hover-img" src="$baseUrl/nps3/lan-huong.jpg" alt="Lan Hương Như Cố">
                </a>
                <p class="episode"><span class="current-episode">24</span><span class="separated">/</span><span class="total-episode">47</span></p>
              </div>
              <div class="info-item-file-index">
                <h3><a href="/lan-huong-nhu-co-against-the-current-f83892.html" title="Lan Hương Như Cố">Lan Hương Như Cố</a></h3>
                <div class="description"><p>
                  <span><i class="fa fa-globe"></i><a href="/tuy-chon/trung-quoc.html?ft=co&co=CN" title="Trung Quốc">CN</a></span>
                  <span><i class="fa fa-clock-o"></i><a href="/tuy-chon/2026.html?ft=ye&ye=2026" title="2026">2026</a></span>
                  <span><i class="fa fa-eye"></i>1.708 </span>
                </p></div>
              </div>
            </div>
        """.trimIndent()

        private val cardB = """
            <div class="item-file-index border-item clearfix">
              <div class="img-item-file-index">
                <a href="/kim-loai-f55555.html" title="Kim Loại">
                  <img class="hover-img" src="$baseUrl/nps3/kim-loai.jpg" alt="Kim Loại">
                </a>
                <p class="episode"><span class="current-episode">1</span><span class="separated">/</span><span class="total-episode">1</span></p>
              </div>
              <div class="info-item-file-index">
                <h3><a href="/kim-loai-f55555.html" title="Kim Loại">Kim Loại</a></h3>
                <div class="description"><p>
                  <span><i class="fa fa-globe"></i><a href="/tuy-chon/my.html?ft=co&co=US" title="Mỹ">US</a></span>
                  <span><i class="fa fa-clock-o"></i><a href="/tuy-chon/2022.html?ft=ye&ye=2022" title="2022">2022</a></span>
                  <span><i class="fa fa-eye"></i>88 </span>
                </p></div>
              </div>
            </div>
        """.trimIndent()

        private val detailHtml = """
            <!DOCTYPE html><html><body>
            <h1 class="title-2">Lan Hương Như Cố</h1>
            <p class="subname">Against The Current</p>
            <div class="detail-movie"><div class="left-detail">
              <div class="header-movie"><div class="img-movie height-standard">
                <img src="$baseUrl/nps3/lan-huong.jpg" alt="Lan Hương Như Cố">
              </div></div>
              <div class="caption-movie">
                <div class="infor-movie">
                  <p>Điểm                        : 8.5</p>
                  <p>Đạo diễn : <a href="/tuy-chon/hoang-dinh-tuong.html?ft=di&di=37059" title="Hoàng Dĩnh Tương">Hoàng Dĩnh Tương</a> </p>
                  <p>Quốc gia : <a href="/tuy-chon/trung-quoc.html?ft=co&co=CN" title="Trung Quốc">Trung Quốc</a> </p>
                  <p>Thể loại : <a href="/phim-tam-ly-tinh-cam-c5.html" title="Phim Tâm Lý - Tình Cảm">Phim Tâm Lý - Tình Cảm</a>, <a href="/phim-co-trang-c14.html" title="Phim Cổ Trang">Phim Cổ Trang</a> </p>
                  <p>Năm sản xuất: <a href="/tuy-chon/2026.html?ft=ye&ye=2026" title="2026">2026</a> </p>
                  <p>Đang phát: 24 / 47 Tập</p>
                </div>
              </div>
            </div>
            <div class="film-desc"><div class="detail-film-desc">Thẩm Gia Lan, trưởng tôn nữ của Thẩm đại học sĩ…</div></div>
            </body></html>
        """.trimIndent()

        private val watchHtml = """
            <!DOCTYPE html><html><body>
            <div class="film-episodes"><div class="listTap"><ul>
              <li><a id="eid1006847" class="episodeLink" href="/xem-phim/lan-huong-nhu-co-against-the-current-f83892-1-e1006847.html">1</a></li>
              <li><a id="eid1006848" class="episodeLink" href="/xem-phim/lan-huong-nhu-co-against-the-current-f83892-2-e1006848.html">2</a></li>
              <li><a id="eid1007951" class="episodeLink" href="/xem-phim/lan-huong-nhu-co-against-the-current-f83892-24-e1007951.html">24</a></li>
            </ul></div></div>
            </body></html>
        """.trimIndent()

        /**
         * The live-search XHR reply — mirrors the LIVE site's shape: country/
         * year `<a>` links are nested INSIDE the film `<a>` (invalid HTML).
         * jsoup splits that outer anchor into fragments, only one of which
         * carries the `<img>`; the parser must read the cover per `li`.
         */
        private val searchJson: String by lazy {
            """{"code":200,"html":"<div class=\"result-group\">Phim</div>\r\n<div class=\"result border-bottom\">\r\n<ul>\r\n<li class=\"result-item\">\r\n<a href=\"/lan-huong-nhu-co-against-the-current-f83892.html\" title=\"Lan Hương Như Cố\">\r\n<div class=\"result-item-box clearfix\">\r\n<div class=\"result-item-image\"><img src=\"$baseUrl/nps3/lan-huong.jpg\" alt=\"Lan Hương Như Cố\"/></div>\r\n<div class=\"result-item-content\">\r\n<p class=\"result-item-title\">Lan Hương Như Cố</p>\r\n<p class=\"result-item-title result-item-title-en\">Against The Current</p>\r\n<div class=\"result-item-price\"><p><span><i class=\"fa fa-globe\"></i><a href=\"/tuy-chon/trung-quoc.html?ft=co&co=CN\" title=\"Trung Quốc\">CN</a></span><span><i class=\"fa fa-clock-o\"></i><a href=\"/tuy-chon/2026.html?ft=ye&ye=2026\" title=\"2026\">2026</a></span></p></div>\r\n</div>\r\n</div>\r\n</a>\r\n</li>\r\n</ul>\r\n</div>","msgdefault":""}"""
        }

        // The watch XHR reply: signed grab iframe (PAI: indexL=0).
        private val paiIframeJson: String
            get() = """{"code":200,"html":"<div id=\"playerEmbed\"><iframe id=\"playerEmbed\" src=\"$baseUrl/grab/lan-huong-nhu-co-against-the-current-f83892.html\"></iframe></div>"}"""

        // The fromEmbed XHR reply: streamc embed iframe (NGC: indexL=1).
        private val ngcIframeJson: String
            get() = """{"code":200,"html":"<div id=\"playerEmbed\"><iframe id=\"playerEmbed\" src=\"$baseUrl/embed.php?hash=abc123\"></iframe></div>"}"""

        private val grabHtml: String by lazy {
            val playlistJson =
                """[{"file":"$baseUrl/pai/master.m3u8","label":"0","type":"hls","default":true}]"""
            val b64 = Base64.getEncoder().encodeToString(playlistJson.toByteArray(Charsets.UTF_8))
            """
            <!DOCTYPE html><html><head><script type="text/javascript">
            var jwplayer = {};
            var v17900506216ab2013d1c8a1 = "$b64";
            var url = '';
            </script></head><body>
            <ul class="listServer"><li class="serverItem" data-index="0">PAI</li><li class="serverItem" data-index="1">NGC</li></ul>
            <script type="text/javascript">
            jQuery('.serverItem').on('click', function () {
                npPhim.setIndexL(jQuery(this).attr('data-index'));
                npPhim.getPlayerAgain();
            });
            npPhim.getPlayerAgain = function () {
                var url = '/xem-phim/lan-huong-nhu-co-against-the-current-f83892-24-e1007951.html?key=rFOkMaSeV2tiZ2lnaWphamNkY1uqpJaij6CdV2thrQ&tim=1790050934&fromEmbed=1&api=nguonphime.site';
            };
            </script></body></html>
            """.trimIndent()
        }

        private val checkerHtml = """
            <!DOCTYPE html><html><head><title>NP Checker</title></head>
            <body><script type="text/javascript">
            setTimeout(function() { window.location.href = "/"; },1000);
            </script>
            Chào mừng bạn đến với chúng tôi, chúc bạn luôn xem phim vui vẻ nhé! Xin vui lòng chờ trong giây lát để chuyển trang!</body></html>
        """.trimIndent()

        private fun bootstrapJson(target: String) =
            """{"video":"abc123","nonce":"n1",
            |"bootstrap":"eyJhbGciOiJIUzI1NiJ9.fake-signature",
            |"api":"$baseUrl${target.substringBefore('?')}?${target.substringAfter('?', "")}",
            |"turnstileEnabled":false,"turnstileSiteKey":"0xfake","ads":{"enabled":false},
            |"server":{"country":"VN"},"sharedCache":false}""".trimMargin()

        private fun issueJson(target: String): String {
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
    }
}

/**
 * OkHttp 5 removed the bundled `JavaNetCookieJar`; this tiny jar is the
 * stand-in for the app's production `WebViewCookieJar` — it keeps the
 * `Set-Cookie` from the NP Checker 302 so the source's retry arrives with a
 * session, exactly like the real app.
 */
private class MemoryCookieJar : CookieJar {
    private val store = CopyOnWriteArrayList<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.filter { it.matches(url) }
}