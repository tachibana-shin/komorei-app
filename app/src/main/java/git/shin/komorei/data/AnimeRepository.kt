package git.shin.komorei.data

import git.shin.komorei.model.Anime
import git.shin.komorei.model.AnimeStatus
import git.shin.komorei.model.CategoryLink
import git.shin.komorei.model.Episode
import git.shin.komorei.model.Genre
import git.shin.komorei.model.Source
import git.shin.komorei.model.StreamData
import git.shin.komorei.model.StreamInfo
import git.shin.komorei.model.StreamType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class AnimeRepository @Inject constructor() {

    // ... (sources and genres remain same)
    val sources: List<Source> = listOf(
        Source("all", "Tổng hợp", "🌐", "2.4.0", "", true, true, 0xFFFF2A55),
        Source("animevietsub", "AnimeVietsub", "⚡", "3.1.2", "https://animevietsub.tv", true, false, 0xFF00C853),
        Source("vuighe", "Vuighe", "🔥", "2.8.0", "https://vuighe4.com", true, false, 0xFFFF9100),
        Source("gogoanime", "GogoAnime", "🌏", "1.9.5", "https://anitaku.to", true, false, 0xFF2979FF),
        Source("hidive", "Hidive", "💎", "1.4.1", "https://hidive.com", true, false, 0xFFAA00FF)
    )

    fun getSource(sourceId: String): Source? {
        return sources.find { it.id == sourceId }
    }

    fun getSourceName(sourceId: String): String {
        return getSource(sourceId)?.name ?: sourceId
    }

    val genres: List<Genre> = listOf(
        Genre("action", "Hành Động", "⚔️", 0xFFE53935, 142),
        Genre("isekai", "Chuyển Sinh", "🌀", 0xFF8E24AA, 98),
        Genre("adventure", "Phiêu Lưu", "🧭", 0xFF1E88E5, 115),
        Genre("harem", "Harem", "🌸", 0xFFD81B60, 64),
        Genre("shounen", "Shounen", "🔥", 0xFFFF8F00, 180),
        Genre("romance", "Lãng Mạn", "💖", 0xFFEC407A, 88),
        Genre("supernatural", "Siêu Nhiên", "👁️", 0xFF5E35B1, 76),
        Genre("school", "Học Đường", "🏫", 0xFF43A047, 92),
        Genre("comedy", "Hài Hước", "🤣", 0xFFFDD835, 120),
        Genre("mystery", "Bí Ẩn", "🕵️", 0xFF3949AB, 55),
        Genre("fantasy", "Giả Tưởng", "✨", 0xFF00ACC1, 134),
        Genre("mecha", "Mecha", "🤖", 0xFF546E7A, 42)
    )

    private val videoUrls = listOf(
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
    )

    /**
     * The primary API to fetch missing data for an Anime.
     * @param anime A "Lite" Anime object containing at least id and sourceId.
     * @param needsDetails Fetch metadata like description, studio, rating, and ALL seasons.
     * @param needsChapters Fetch only the episode list for the specific id provided in [anime].
     */
    suspend fun getAnimeUpdate(
        anime: Anime,
        needsDetails: Boolean,
        needsChapters: Boolean
    ): Anime {
        delay(400) // Simulating network
        
        // Find the "Master" mock data for this ID
        val databaseMatch = allAnimes.find { it.id == anime.id } ?: anime

        return anime.copy(
            // Metadata update
            description = if (needsDetails) databaseMatch.description else anime.description,
            studio = if (needsDetails) databaseMatch.studio else anime.studio,
            rating = if (needsDetails) databaseMatch.rating else anime.rating,
            status = if (needsDetails) databaseMatch.status else anime.status,
            seasons = if (needsDetails) databaseMatch.seasons else anime.seasons,
            
            // Chapter (Episode) update - strictly for current anime.id
            episodes = if (needsChapters) databaseMatch.episodes else anime.episodes
        )
    }

    suspend fun getStreamList(anime: Anime, episode: Episode): List<StreamInfo> {
        delay(200)
        return listOf(
            StreamInfo("server_fhd", "Server 1 (FHD)", "1080p"),
            StreamInfo("server_vip", "Storage VIP", "1080p"),
            StreamInfo("server_hls", "HLS Stream", "720p")
        )
    }

    suspend fun getStream(anime: Anime, episode: Episode, stream: StreamInfo): StreamData {
        delay(400)
        val url = if (stream.id == "server_hls") {
            "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"
        } else {
            videoUrls[Random.nextInt(videoUrls.size)]
        }
        
        return StreamData(
            url = url,
            type = if (url.endsWith(".m3u8")) StreamType.HLS else StreamType.MP4,
            headers = mapOf(
                "Referer" to (sources.find { it.id == anime.sourceId }?.name ?: "Komorei"),
                "User-Agent" to "Komorei/1.0"
            )
        )
    }

    // --- Mock Data Helpers ---
    
    private fun generateEpisodes(animeId: String, sourceId: String, count: Int, titlePrefix: String): List<Episode> {
        return (1..count).map { i ->
            Episode(
                id = "${animeId}_ep_$i",
                animeId = animeId,
                sourceId = sourceId,
                episodeNumber = i.toString(),
                title = "Tập $i - $titlePrefix",
                quality = "1080p FHD"
            )
        }
    }

    val allAnimes: List<Anime> by lazy {
        val soloS1Eps = generateEpisodes("solo_leveling", "animevietsub", 12, "Thợ Săn Hạng E")
        val soloS2Eps = generateEpisodes("solo_leveling_s2", "animevietsub", 13, "Chúa Tể Bóng Tối")

        val dandadanS1Eps = generateEpisodes("dandadan", "animevietsub", 12, "Chạm Trán Siêu Nhiên")
        val dandadanS2Eps = generateEpisodes("dandadan_s2", "animevietsub", 12, "Cuộc Chiến Quỷ Ác Tà")

        val jjkS1Eps = generateEpisodes("jujutsu_kaisen", "gogoanime", 24, "Ngón Tay Sukuna")
        val jjkS2Eps = generateEpisodes("jujutsu_kaisen_s2", "gogoanime", 23, "Thảm Kịch Shibuya")

        val dsS1Eps = generateEpisodes("demon_slayer_s1", "vuighe", 11, "Phố Đèn Đỏ")
        val dsS2Eps = generateEpisodes("demon_slayer_s2", "vuighe", 11, "Làng Thợ Rèn")
        val dsS3Eps = generateEpisodes("demon_slayer_hashira", "vuighe", 8, "Khóa Huấn Luyện")

        val mushokuS1Eps = generateEpisodes("mushoku_tensei", "hidive", 12, "Học Viện Phép Thuật")
        val mushokuS2Eps = generateEpisodes("mushoku_tensei_s2", "hidive", 12, "Mê Cung Rapan")

        listOf(
            Anime(
                id = "solo_leveling_s2",
                sourceId = "animevietsub",
                title = "Solo Leveling: Arise from the Shadow",
                originalTitle = "Ore dake Level Up na Ken Season 2",
                posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200",
                description = "Sung Jin-woo tiếp tục hành trình thức tỉnh sức mạnh Chúa Tể Bóng Tối, đối mặt với các Thợ Săn Cấp Quốc Gia và giải cứu thế giới khỏi hiểm họa hầm ngục.",
                episodeCount = 13,
                currentEpisode = "Tập 10/13",
                rating = 4.95f,
                ratingCount = 12500,
                status = AnimeStatus.ONGOING,
                releaseYear = CategoryLink("2025"),
                genres = listOf(CategoryLink("Hành Động"), CategoryLink("Siêu Nhiên")),
                authors = listOf(CategoryLink("Chugong")),
                studio = CategoryLink("A-1 Pictures"),
                seasonOf = null,
                isFeatured = true,
                views = 2800000,
                nextEpisodeAirInfo = "Tập 11 phát sóng lúc 22:30 Thứ Bảy ngày 12/10",
                episodes = soloS2Eps,
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("solo_leveling", "Phần 1: Thức Tỉnh"),
                    git.shin.komorei.model.AnimeSeason("solo_leveling_s2", "Phần 2: Arise")
                )
            ),
            Anime(
                id = "frieren_journey",
                sourceId = "vuighe",
                title = "Frieren: Pháp Sư Tiễn Táng",
                originalTitle = "Sousou no Frieren",
                posterUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=1200",
                description = "Hành trình sâu lắng của pháp sư Elf Frieren sau khi Đội Anh Hùng đánh bại Quỷ Vương, đi tìm ý nghĩa của cuộc sống và sự hữu hạn của thời gian.",
                episodeCount = 28,
                currentEpisode = "Tập 28/28 End",
                rating = 4.98f,
                ratingCount = 8500,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2024"),
                genres = listOf(CategoryLink("Phiêu Lưu"), CategoryLink("Giả Tưởng")),
                authors = listOf(CategoryLink("Kanehito Yamada")),
                studio = CategoryLink("Madhouse"),
                seasonOf = null,
                isFeatured = true,
                views = 4100000,
                episodes = generateEpisodes("frieren_journey", "vuighe", 28, "Hành Trình Mới"),
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("frieren_journey", "Phần 1: Hành Trình Mới")
                )
            ),
            Anime(
                id = "dandadan",
                sourceId = "animevietsub",
                title = "Dandadan: Cuộc Chiến Siêu Nhiên",
                originalTitle = "Dandadan",
                posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200",
                description = "Momo Ayase tin vào ma quỷ nhưng không tin người ngoài hành tinh. Okarun ngược lại. Một vụ cá cược định mệnh đưa họ vào thế giới hỗn loạn của quái vật và thế lực kỳ bí.",
                episodeCount = 24,
                currentEpisode = "Tập 12/24",
                rating = 4.91f,
                ratingCount = 5200,
                status = AnimeStatus.ONGOING,
                releaseYear = CategoryLink("2024"),
                genres = listOf(CategoryLink("Hành Động"), CategoryLink("Siêu Nhiên")),
                authors = listOf(CategoryLink("Yukinobu Tatsu")),
                studio = CategoryLink("Science SARU"),
                seasonOf = null,
                isFeatured = true,
                views = 1900000,
                nextEpisodeAirInfo = "Tập tiếp theo (Tập 13) phát lúc 23:00 Thứ Năm hàng tuần",
                episodes = dandadanS1Eps,
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("dandadan", "Phần 1: Chạm Trán"),
                    git.shin.komorei.model.AnimeSeason("dandadan_s2", "Phần 2: Quỷ Ác Tà")
                )
            ),
            Anime(
                id = "jujutsu_kaisen_s2",
                sourceId = "gogoanime",
                title = "Jujutsu Kaisen: Biến Cố Shibuya",
                originalTitle = "Jujutsu Kaisen 2nd Season",
                posterUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=1200",
                description = "Cuộc chiến khốc liệt nhất lịch sử Chú Thuật Sư tại ngã tư Shibuya khi Gojo Satoru bị phong ấn trong Ngục Môn Cương.",
                episodeCount = 23,
                currentEpisode = "Tập 23/23 End",
                rating = 4.96f,
                ratingCount = 15000,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2023"),
                genres = listOf(CategoryLink("Hành Động"), CategoryLink("Shounen")),
                authors = listOf(CategoryLink("Gege Akutami")),
                studio = CategoryLink("MAPPA"),
                seasonOf = null,
                isFeatured = false,
                views = 5600000,
                episodes = jjkS2Eps,
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("jujutsu_kaisen", "Phần 1: Chú Thuật"),
                    git.shin.komorei.model.AnimeSeason("jujutsu_kaisen_s2", "Phần 2: Sự Cố Shibuya")
                )
            ),
            Anime(
                id = "demon_slayer_hashira",
                sourceId = "vuighe",
                title = "Thanh Gươm Diệt Quỷ: Đại Trụ Đặc Huấn",
                originalTitle = "Kimetsu no Yaiba: Hashira Geiko-hen",
                posterUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1200",
                description = "Tanjiro và Sát Quỷ Đội bắt đầu đợt tập huấn khắc nghiệt dưới sự hướng dẫn của các Trụ Cột trước trận quyết chiến tại Vô Hạn Thành.",
                episodeCount = 8,
                currentEpisode = "Tập 8/8 End",
                rating = 4.88f,
                ratingCount = 9800,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2024"),
                genres = listOf(CategoryLink("Hành Động"), CategoryLink("Shounen")),
                authors = listOf(CategoryLink("Koyoharu Gotouge")),
                studio = CategoryLink("ufotable"),
                seasonOf = null,
                isFeatured = true,
                views = 3400000,
                episodes = dsS3Eps,
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("demon_slayer_s1", "Phần 1: Phố Đèn Đỏ"),
                    git.shin.komorei.model.AnimeSeason("demon_slayer_s2", "Phần 2: Làng Thợ Rèn"),
                    git.shin.komorei.model.AnimeSeason("demon_slayer_hashira", "Phần 3: Đại Trụ Đặc Huấn")
                )
            ),
            Anime(
                id = "mushoku_tensei_s2",
                sourceId = "hidive",
                title = "Thất Nghiệp Chuyển Sinh: Mùa 2 Phần 2",
                originalTitle = "Mushoku Tensei II: Isekai Ittara Honki Dasu Part 2",
                posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200",
                description = "Rudeus Greyrat đến Mê Cung Rapan để giải cứu mẹ Zenith cùng cha Paul và sư phục Roxy, trải qua thử thách đầy cảm xúc.",
                episodeCount = 12,
                currentEpisode = "Tập 12/12 End",
                rating = 4.93f,
                ratingCount = 11000,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2024"),
                genres = listOf(CategoryLink("Chuyển Sinh"), CategoryLink("Phiêu Lưu")),
                authors = listOf(CategoryLink("Rifujin na Magonote")),
                studio = CategoryLink("Studio Bind"),
                seasonOf = null,
                isFeatured = false,
                views = 2200000,
                episodes = mushokuS2Eps,
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("mushoku_tensei", "Phần 1: Học Viện"),
                    git.shin.komorei.model.AnimeSeason("mushoku_tensei_s2", "Phần 2: Mê Cung Rapan")
                )
            ),
            Anime(
                id = "kimi_no_na_wa",
                sourceId = "animevietsub",
                title = "Your Name (Tên Cậu Là Gì?)",
                originalTitle = "Kimi no Na wa.",
                posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200",
                description = "Kiệt tác điện ảnh của đạo diễn Makoto Shinkai kể về cuộc hoán đổi thân xác kỳ diệu giữa Mitsuha ở vùng quê Itomori và Taki ở Tokyo náo nhiệt.",
                episodeCount = 1,
                currentEpisode = "Bản Chiếu Rạp FHD",
                rating = 4.99f,
                ratingCount = 25000,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2016"),
                genres = listOf(CategoryLink("Romance"), CategoryLink("Siêu Nhiên")),
                authors = listOf(CategoryLink("Makoto Shinkai")),
                studio = CategoryLink("CoMix Wave Films"),
                seasonOf = null,
                isFeatured = false,
                views = 8900000,
                episodes = generateEpisodes("kimi_no_na_wa", "animevietsub", 1, "Bản Chiếu Rạp Full HD Vietsub"),
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("kimi_no_na_wa", "Bản Chiếu Rạp")
                )
            ),
            Anime(
                id = "suzume_no_tojimari",
                sourceId = "vuighe",
                title = "Khóa Chặt Cửa Nào Suzume",
                originalTitle = "Suzume no Tojimari",
                posterUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=1200",
                description = "Cô gái 17 tuổi Suzume tình cờ gặp một thanh niên bí ẩn tìm kiếm một cánh cửa. Hai người cùng lên đường khóa những cánh cửa tai họa khắp Nhật Bản.",
                episodeCount = 1,
                currentEpisode = "Bản Chiếu Rạp FHD",
                rating = 4.92f,
                ratingCount = 18000,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2022"),
                genres = listOf(CategoryLink("Phiêu Lưu"), CategoryLink("Siêu Nhiên")),
                authors = listOf(CategoryLink("Makoto Shinkai")),
                studio = CategoryLink("CoMix Wave Films"),
                seasonOf = null,
                isFeatured = false,
                views = 4700000,
                episodes = generateEpisodes("suzume_no_tojimari", "vuighe", 1, "Bản Chiếu Rạp Chuẩn Rạp"),
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("suzume_no_tojimari", "Bản Chiếu Rạp")
                )
            ),
            Anime(
                id = "kaiju_no_8",
                sourceId = "gogoanime",
                title = "Kaiju Số 8",
                originalTitle = "Kaijuu 8-gou",
                posterUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=1200",
                description = "Kafka Hibino 32 tuổi biến thành quái thú Kaiju Số 8 nhưng vẫn nuôi ước mơ gia nhập Lực Lượng Phòng Vệ Nhật Bản.",
                episodeCount = 12,
                currentEpisode = "Tập 12/12 End",
                rating = 4.87f,
                ratingCount = 7600,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2024"),
                genres = listOf(CategoryLink("Hành Động"), CategoryLink("Sci-Fi")),
                authors = listOf(CategoryLink("Naoya Matsumoto")),
                studio = CategoryLink("Production I.G"),
                seasonOf = null,
                isFeatured = false,
                views = 2500000,
                episodes = generateEpisodes("kaiju_no_8", "gogoanime", 12, "Thức Tỉnh Kaiju"),
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("kaiju_no_8", "Phần 1: Thức Tỉnh")
                )
            ),
            Anime(
                id = "solo_leveling",
                sourceId = "animevietsub",
                title = "Solo Leveling (Phần 1)",
                originalTitle = "Ore dake Level Up na Ken",
                posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200",
                description = "Hành trình từ thợ săn yếu nhất đến đỉnh cao.",
                episodeCount = 12,
                currentEpisode = "Full 12/12",
                rating = 4.9f,
                ratingCount = 8000,
                status = AnimeStatus.COMPLETED,
                releaseYear = CategoryLink("2024"),
                genres = listOf(CategoryLink("Hành Động")),
                authors = listOf(CategoryLink("Chugong")),
                studio = CategoryLink("A-1 Pictures"),
                seasonOf = null,
                episodes = soloS1Eps,
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("solo_leveling", "Phần 1: Thức Tỉnh"),
                    git.shin.komorei.model.AnimeSeason("solo_leveling_s2", "Phần 2: Arise")
                )
            )
        )
    }

    /**
     * Get featured anime for Home tab
     */
    fun getFeaturedAnime(sourceId: String): List<Anime> {
        val list = if (sourceId == "all") allAnimes else allAnimes.filter { it.sourceId == sourceId }
        val featured = list.filter { it.isFeatured }
        return if (featured.isNotEmpty()) featured else list.take(3)
    }

    /**
     * Get grouped sections for the Home Hub by source
     */
    fun getSectionsForSource(sourceId: String): Map<String, List<Anime>> {
        val baseList = if (sourceId == "all") allAnimes else allAnimes.filter { it.sourceId == sourceId }
        return linkedMapOf(
            "Mới Cập Nhật" to baseList.filter { it.id in listOf("dandadan", "demon_slayer_hashira", "kaiju_no_8") },
            "Xu Hướng Mùa Này" to baseList.filter { it.id in listOf("solo_leveling_s2", "frieren_journey") },
            "Anime Bộ Hot" to baseList.filter { it.id in listOf("jujutsu_kaisen_s2", "mushoku_tensei_s2", "frieren_journey") },
            "Anime Lẻ Chiếu Rạp" to baseList.filter { it.id in listOf("kimi_no_na_wa", "suzume_no_tojimari") }
        )
    }

    /**
     * Parallel multi-source search
     */
    suspend fun searchMultiSource(
        query: String,
        selectedGenreId: String? = null
    ): Map<Source, List<Anime>> = coroutineScope {
        delay(120) // simulation

        val activeSources = sources.filter { !it.isAggregator }

        val genreName = selectedGenreId?.let { id ->
            genres.find { it.id == id }?.name
        }

        val deferredResults = activeSources.map { source ->
            async {
                val matched = allAnimes.filter { anime ->
                    val matchesSource = anime.sourceId == source.id || anime.sourceId == "all"
                    val matchesQuery = query.isBlank() ||
                            anime.title.contains(query, ignoreCase = true) ||
                            anime.originalTitle.contains(query, ignoreCase = true)
                    val matchesGenre = genreName == null || anime.genres.any {
                        it.name.equals(
                            genreName,
                            ignoreCase = true
                        )
                    }

                    matchesSource && matchesQuery && matchesGenre
                }
                source to matched
            }
        }

        deferredResults.map { it.await() }
            .filter { (_, results) -> results.isNotEmpty() }
            .toMap()
    }
}
