package git.shin.komorei.sdk

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.runner.AnimeStatus
import git.shin.komorei.sdk.runner.DeepLinkResult
import git.shin.komorei.sdk.runner.FilterKind
import git.shin.komorei.sdk.runner.FilterValue
import git.shin.komorei.sdk.runner.HostDefaultValue
import git.shin.komorei.sdk.runner.Listing
import git.shin.komorei.sdk.runner.ListingKind
import git.shin.komorei.sdk.runner.LinkValue
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
 * The app's own fake source (`sources/fake-vi-source`, packaged at
 * `app/src/main/assets/sources/fake-vi-source.krx`) driven through the REAL
 * runner (uniffi bindings + linux cdylib) and the REAL Kotlin host
 * ([KrxHostImpl] — Jsoup + OkHttp + SharedPreferences). Same harness as
 * [KrxRunnerIntegrationTest], but against the rich Vietnamese catalog this
 * source will eventually wire into the app (replacing the mocked
 * [git.shin.komorei.data.AnimeRepository]).
 *
 * The krx is located via `-Dkomorei.test.fakeKrx` (set by app/build.gradle.kts).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FakeViSourceRunnerIntegrationTest {

    private lateinit var host: KrxHostImpl
    private lateinit var runner: git.shin.komorei.sdk.runner.KomoreiRunner

    companion object {
        private val fakeKrx: String = System.getProperty("komorei.test.fakeKrx")
            ?: error("missing -Dkomorei.test.fakeKrx (set by app/build.gradle.kts)")
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
        runner = KrxManager.load(host, File(fakeKrx).readBytes())
    }

    // ── search ──────────────────────────────────────────────────────────────

    @Test
    fun `search returns paginated catalog`() {
        val page1 = runner.search(null, 1, emptyList())
        assertEquals(15, page1.entries.size) // PAGE_SIZE
        assertTrue(page1.hasNextPage)

        val page2 = runner.search(null, 2, emptyList())
        assertEquals(7, page2.entries.size) // 22 in catalog (15 + 7)
        assertFalse(page2.hasNextPage)

        val first = page1.entries.first()
        assertEquals("solo_leveling_s2", first.key)
        assertEquals("vi.fake-source", first.sourceId)
        assertEquals("Solo Leveling: Arise from the Shadow", first.title)
        assertEquals("https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600", first.cover)
        assertEquals(AnimeStatus.ONGOING, first.status)
        assertEquals("Tập 10/13", first.currentEpisode)
        assertEquals(13, first.episodeCount)
        assertTrue(first.genres.any { it.name == "Hành Động" })
    }

    @Test
    fun `search query filters by title and original title`() {
        val byTitle = runner.search("Frieren", 1, emptyList()).entries
        assertEquals(1, byTitle.size)
        assertEquals("frieren_journey", byTitle.single().key)

        val byOriginal = runner.search("Kimetsu no Yaiba", 1, emptyList()).entries
        assertEquals(1, byOriginal.size)
        assertEquals("demon_slayer_hashira", byOriginal.single().key)

        assertEquals(0, runner.search("không-tồn-tại", 1, emptyList()).entries.size)
    }

    @Test
    fun `genre and status filters narrow the catalog`() {
        // genre MultiSelect → only entries containing the genre
        val action = runner.search(
            null, 1,
            listOf(FilterValue.MultiSelect("genres", listOf("Hành Động"), emptyList())),
        ).entries
        assertTrue(action.isNotEmpty())
        assertTrue(action.all { it.genres.any { g -> g.name == "Hành Động" } })

        // status Select → only Completed or Ongoing
        val done = runner.search(
            null, 1,
            listOf(FilterValue.Select("status", "Hoàn thành")),
        ).entries
        assertTrue(done.isNotEmpty())
        assertTrue(done.all { it.status == AnimeStatus.COMPLETED })

        val ongoing = runner.search(
            null, 1,
            listOf(FilterValue.Select("status", "Đang phát")),
        ).entries
        assertEquals(4, ongoing.size)
        assertTrue(ongoing.all { it.status == AnimeStatus.ONGOING })
    }

    @Test
    fun `sort filter reorders by rating or views`() {
        val byRating = runner.search(
            null, 1,
            listOf(FilterValue.Sort("sort", 0, ascending = false)),
        ).entries
        val ratings = byRating.map { it.rating!! }
        assertEquals(ratings.sortedDescending(), ratings)

        val byViews = runner.search(
            null, 1,
            listOf(FilterValue.Sort("sort", 1, ascending = false)),
        ).entries
        val views = byViews.map { it.views }
        assertEquals(views.sortedDescending(), views)
    }

    // ── animeUpdate ─────────────────────────────────────────────────────────

    @Test
    fun `anime update fills details and chapters for the same season`() {
        val lite = runner.search("Solo Leveling: Arise", 1, emptyList()).entries.single()
        assertEquals("solo_leveling_s2", lite.key)
        assertNull(lite.description) // lite

        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = true)
        assertTrue(full.description!!.contains("Chúa Tể Bóng Tối"))
        assertEquals("https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200", full.banner)
        assertEquals(AnimeStatus.ONGOING, full.status)
        assertEquals("2025", full.releaseYear!!.name)
        assertEquals("Chugong", full.authors[0].name)
        assertEquals("A-1 Pictures", full.studio!!.name)
        assertEquals(4.95f, full.rating!!, 0.0f)
        assertEquals(12500, full.ratingCount!!)
        assertEquals(2_800_000, full.views)
        assertTrue(full.nextEpisodeAirInfo!!.contains("Tập 11"))
        assertEquals("FHD", full.qualityTag)
        assertEquals(2, full.seasons.size)
        assertEquals("solo_leveling_s2", full.seasons[1].id)

        // needsChapters with the current-session key → 13 episodes for this season
        val eps = full.episodes!!
        assertEquals(13, eps.size)
        assertEquals("solo_leveling_s2_ep_1", eps[0].key)
        assertEquals("1", eps[0].episodeNumber)
        assertEquals("Tập 1 - Solo Leveling: Arise from the Shadow", eps[0].title)
        assertNotNull(eps[0].thumbnail)
        assertNotNull(eps[0].dateUploaded)
        assertEquals(1440L, eps[0].durationSeconds)
        assertEquals("FHD", eps[0].quality)
        assertEquals("vi", eps[0].language)
        assertFalse(eps[0].locked)
    }

    @Test
    fun `chapters stay scoped to the asked season`() {
        // requests chapters for the parent season key only
        val lite = runner.search("One Piece", 1, emptyList()).entries.single()
        val full = runner.animeUpdate(lite, needsDetails = false, needsChapters = true)
        val eps = full.episodes!!
        assertEquals(64, eps.size) // capped for mega-series
        assertTrue(eps.all { it.key.startsWith("one_piece_ep_") })
    }

    // ── streams + defaults read/write ───────────────────────────────────────

    @Test
    fun `stream list serves three servers with playable data`() {
        val lite = runner.search("Frieren", 1, emptyList()).entries.single()
        val full = runner.animeUpdate(lite, true, true)
        val episode = full.episodes!!.first()

        val servers = runner.streamList(full, episode)
        assertEquals(3, servers.size)
        // default order: hls, mp4_720, mp4_fhd
        assertEquals(listOf("hls", "mp4_720", "mp4_fhd"), servers.map { it.key })
        assertEquals("Chill HLS", servers[0].name)
        assertEquals("1080p FHD", servers[0].quality)

        val hls = runner.stream(full, episode, servers[0])
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", hls.url)
        assertEquals(StreamType.HLS, hls.streamType)
        assertTrue(hls.isContent)
        assertEquals("Komorei/1.0", hls.headers["User-Agent"])
        assertEquals(1, hls.subtitles.size)
        assertEquals("vi", hls.subtitles[0].language)
        assertEquals(0L, hls.intro!!.startMs)
        assertEquals(90_000L, hls.intro!!.endMs)
        assertNotNull(hls.outro)

        val mp4 = runner.stream(full, episode, servers[1])
        assertEquals(
            "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_5MB.mp4",
            mp4.url,
        )
        assertEquals(StreamType.MP4, mp4.streamType)
        assertTrue(mp4.subtitles.isEmpty())
        assertEquals(0L, mp4.intro!!.startMs)
        assertNull(mp4.outro)
    }

    @Test
    fun `prefer_fhd default flips server order through real shared preferences`() {
        val lite = runner.search("Frieren", 1, emptyList()).entries.single()
        val full = runner.animeUpdate(lite, true, true)
        val episode = full.episodes!!.first()

        assertEquals(
            listOf("hls", "mp4_720", "mp4_fhd"),
            runner.streamList(full, episode).map { it.key },
        )

        // write via the host (real SharedPreferences-backed defaults)
        host.defaultsSet("prefer_fhd", HostDefaultValue.Bool(true))
        assertEquals(
            listOf("mp4_fhd", "mp4_720", "hls"),
            runner.streamList(full, episode).map { it.key },
        )

        host.defaultsSet("prefer_fhd", HostDefaultValue.Bool(false))
        assertEquals(
            listOf("hls", "mp4_720", "mp4_fhd"),
            runner.streamList(full, episode).map { it.key },
        )
    }

    // ── filters / settings ──────────────────────────────────────────────────

    @Test
    fun `filters decode to search sort genres status and year`() {
        val filters = runner.filters()
        val byId = filters.associateBy { it.id }
        assertTrue(byId.containsKey("search"))
        assertTrue(byId.containsKey("sort"))
        assertTrue(byId.containsKey("genres"))
        assertTrue(byId.containsKey("status"))
        assertTrue(byId.containsKey("year"))

        val genres = byId.getValue("genres").kind as FilterKind.MultiSelect
        assertTrue(genres.isGenre)
        assertTrue(genres.usesTagStyle)
        assertTrue(genres.options.contains("Hành Động"))
        assertTrue(genres.options.contains("Chuyển Sinh"))

        val status = byId.getValue("status").kind as FilterKind.Select
        assertEquals(listOf("Tất cả", "Đang phát", "Hoàn thành"), status.options)

        val year = byId.getValue("year").kind as FilterKind.Range
        assertEquals(1996.0f, year.min!!, 0.0f)
        assertEquals(2025.0f, year.max!!, 0.0f)

        val sort = byId.getValue("sort").kind as FilterKind.Sort
        assertEquals(listOf("Đánh giá", "Phổ biến", "A-Z"), sort.options)
    }

    @Test
    fun `settings expose toggles including prefer_fhd`() {
        val settings = runner.settings()
        val byKey = settings.associateBy { it.key }
        assertTrue(byKey.containsKey("prefer_fhd"))
        assertTrue(byKey.containsKey("show_intro"))
        val prefer = byKey.getValue("prefer_fhd")
        assertEquals("Ưu tiên 1080p", prefer.title)
        assertFalse((prefer.value as git.shin.komorei.sdk.runner.SettingValue.Toggle).default)
    }

    // ── listings / home ─────────────────────────────────────────────────────

    @Test
    fun `listings expose the four rails`() {
        val listings = runner.listings()
        assertEquals(listOf("latest", "popular", "ongoing", "completed"), listings.map { it.id })
        assertEquals(listOf("Mới nhất", "Phổ biến", "Đang phát", "Hoàn thành"), listings.map { it.name })
        assertTrue(listings.all { it.kind == ListingKind.LIST })

        val popular = runner.animeList(listings[1], 1)
        assertEquals(15, popular.entries.size)
        // "popular" sorts by views desc — One Piece (62M) ranks first
        assertEquals("one_piece", popular.entries.first().key)
        assertTrue(popular.entries.first().views >= popular.entries.last().views)
    }

    @Test
    fun `ongoing and completed listings only contain their statuses`() {
        val ongoing = runner.animeList(Listing("ongoing", "Đang phát", ListingKind.LIST), 1)
        assertTrue(ongoing.entries.all { it.status == AnimeStatus.ONGOING })
        assertFalse(ongoing.entries.isEmpty())

        val completed = runner.animeList(Listing("completed", "Hoàn thành", ListingKind.LIST), 1)
        assertTrue(completed.entries.all { it.status == AnimeStatus.COMPLETED })
        assertFalse(completed.entries.isEmpty())
    }

    @Test
    fun `home exposes hero and rails with rich data`() {
        val home = runner.home()
        // The fake source emits ALL 7 HomeComponentValue variants, in order.
        assertEquals(
            listOf("Khám Phá", "Nổi Bật", "Đang Hot", "Mới Cập Nhật", "Phổ Biến Nhất", "Thể Loại", "Liên Kết"),
            home.components.map { it.title.orEmpty() },
        )

        // 1. ImageScroller — banner links with images
        val promo = home.components[0].value as git.shin.komorei.sdk.runner.HomeComponentValue.ImageScroller
        assertTrue(promo.links.isNotEmpty())
        assertTrue(promo.links.all { it.imageUrl != null })
        assertEquals(4.0f, promo.autoScrollInterval ?: 0f, 0.01f)

        // 2. BigScroller — featured hero
        val hero = home.components[1].value as git.shin.komorei.sdk.runner.HomeComponentValue.BigScroller
        assertEquals(5, hero.entries.size) // all is_featured entries
        assertTrue(hero.entries.any { it.key == "solo_leveling_s2" })
        assertTrue(hero.entries.any { it.key == "conan" })

        // 3. Scroller — small rail (top by views) with a listing
        val hot = home.components[2].value as git.shin.komorei.sdk.runner.HomeComponentValue.Scroller
        assertTrue(hot.entries.isNotEmpty())
        assertEquals("hot", hot.listing!!.id)
        assertTrue(hot.entries.all { it.value is LinkValue.Anime })
        assertTrue("Scroller links carry poster covers", hot.entries.all { it.imageUrl != null })

        // 4. AnimeEpisodeList — recent updates
        val latest = home.components[3].value as git.shin.komorei.sdk.runner.HomeComponentValue.AnimeEpisodeList
        assertEquals(10, latest.entries.size)
        assertEquals("latest", latest.listing!!.id)
        assertTrue("episodes carry the upload timestamp", latest.entries.all { it.episode.dateUploaded != null })

        // 5. AnimeList — popular ranking
        val ranking = home.components[4].value as git.shin.komorei.sdk.runner.HomeComponentValue.AnimeList
        assertTrue(ranking.ranking)
        assertTrue("page_size set → paged 2-column layout", ranking.pageSize != null)
        assertTrue("more entries than one page → paging active", ranking.entries.size > ranking.pageSize!!)
        assertEquals("popular", ranking.listing!!.id)
        assertTrue("AnimeList links carry poster covers", ranking.entries.all { it.imageUrl != null })

        // 6. Filters — one chip per genre with an actionable MultiSelect value
        val genreChips = home.components[5].value as git.shin.komorei.sdk.runner.HomeComponentValue.Filters
        assertEquals(12, genreChips.v1.size)
        assertTrue(genreChips.v1.all { it.values != null })
        assertEquals(
            listOf("Hành Động", "Chuyển Sinh", "Phiêu Lưu", "Harem", "Shounen", "Lãng Mạn",
                "Siêu Nhiên", "Học Đường", "Hài Hước", "Bí Ẩn", "Giả Tưởng", "Mecha"),
            genreChips.v1.map { it.title },
        )

        // 7. Links — plain navigation shortcuts
        val links = home.components[6].value as git.shin.komorei.sdk.runner.HomeComponentValue.Links
        assertEquals(4, links.v1.size)
        assertTrue(links.v1.any { it.value is LinkValue.Listing })
        assertTrue(links.v1.any { it.value is LinkValue.Url })
    }

    // ── deep link ───────────────────────────────────────────────────────────

    @Test
    fun `deep link resolves anime episode and listing`() {
        val anime = runner.deepLink("https://komorei.example/anime/frieren_journey")
        assertNotNull(anime)
        assertTrue(anime is DeepLinkResult.Anime)
        assertEquals("frieren_journey", (anime as DeepLinkResult.Anime).key)

        val episode = runner.deepLink("https://komorei.example/watch/solo_leveling_s2/solo_leveling_s2_ep_1")
        assertTrue(episode is DeepLinkResult.Episode)
        assertEquals("solo_leveling_s2", (episode as DeepLinkResult.Episode).animeKey)
        assertEquals("solo_leveling_s2_ep_1", episode.key)

        val listing = runner.deepLink("https://komorei.example/list/popular")
        assertTrue(listing is DeepLinkResult.Listing)
        assertEquals("popular", (listing as DeepLinkResult.Listing).v1.id)

        assertNull(runner.deepLink("https://komorei.example/nope"))
    }

    // ── interceptors / mappings ─────────────────────────────────────────────

    @Test
    fun `segment interceptors default to identity`() {
        val url = "https://komorei.example/media/seg-1.ts"
        assertEquals(url, runner.interceptSegmentUrl(null, url))
        val data = byteArrayOf(0x47, 0x40, 0x00, 0x10)
        assertArrayEquals(data, runner.interceptSegmentData(null, url, data))
    }

    @Test
    fun `mappings convert fake source records to app models`() {
        val lite = runner.search("Frieren", 1, emptyList()).entries.single().toAppModel()
        assertEquals("frieren_journey", lite.id)
        assertEquals("Frieren: Pháp Sư Tiễn Táng", lite.title)
        assertTrue(lite.posterUrl.startsWith("https://images.unsplash.com/"))
        assertEquals(28, lite.episodeCount)

        val bindingFull =
            runner.animeUpdate(runner.search("Frieren", 1, emptyList()).entries.single(), true, true)
        val full = bindingFull.toAppModel()
        assertEquals(28, full.episodes.size)
        assertEquals(1, full.seasons.size)
        assertTrue(full.description!!.isNotEmpty())
        assertEquals("2024", full.releaseYear?.name)
        assertEquals("Madhouse", full.studio?.name)
        assertEquals(git.shin.komorei.model.AnimeStatus.COMPLETED, full.status)

        val modelStream = runner
            .stream(bindingFull, bindingFull.episodes!!.first(), runner.streamList(bindingFull, bindingFull.episodes!!.first())[0])
            .toAppModel()
        assertEquals(git.shin.komorei.model.StreamType.HLS, modelStream.type)
        assertEquals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", modelStream.url)
        assertTrue(modelStream.isContent)
        assertEquals(0L, modelStream.intro?.startMs)
    }
}