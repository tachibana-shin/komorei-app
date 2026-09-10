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
        Genre("shounen", "Shounen", "🔥", 0xFFFF8F00, 180)
    )

    private val videoUrls = listOf(
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
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
        val soloS1Eps = generateEpisodes("solo_leveling", "animevietsub", 12, "Thức Tỉnh")
        val soloS2Eps = generateEpisodes("solo_leveling_s2", "animevietsub", 13, "Chúa Tể")

        listOf(
            Anime(
                id = "solo_leveling_s2",
                sourceId = "animevietsub",
                title = "Solo Leveling: Arise from the Shadow",
                originalTitle = "Ore dake Level Up na Ken Season 2",
                posterUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600",
                bannerUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1200",
                description = "Sung Jin-woo tiếp tục hành trình thức tỉnh sức mạnh Chúa Tể Bóng Tối...",
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
                episodes = soloS2Eps,
                seasons = listOf(
                    git.shin.komorei.model.AnimeSeason("solo_leveling", "Phần 1: Thức Tỉnh"),
                    git.shin.komorei.model.AnimeSeason("solo_leveling_s2", "Phần 2: Arise")
                )
            ),
            // Other animes would follow same pattern...
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

    fun getFeaturedAnime(sourceId: String): List<Anime> = allAnimes.take(3)
    fun getSectionsForSource(sourceId: String): Map<String, List<Anime>> = mapOf("Mới Cập Nhật" to allAnimes)
    suspend fun searchMultiSource(query: String, selectedGenreId: String? = null): Map<Source, List<Anime>> = emptyMap()
}
