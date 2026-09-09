package git.shin.komorei.model

/**
 * Represents a season or part of an anime (e.g. Phần 1, Phần 2, OVA, Movie).
 */
data class AnimeSeason(
    val seasonNumber: Int,
    val title: String,
    val episodes: List<Episode> = emptyList()
)

/**
 * Main Anime entity representing metadata across sources.
 */
data class Anime(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val originalTitle: String,
    val posterUrl: String,
    val bannerUrl: String,
    val description: String,
    val episodeCount: Int,
    val currentEpisodeBadge: String, // e.g. "Tập 12/12", "Tập 24 End", "HD Vietsub"
    val rating: Float, // e.g. 4.9
    val releaseYear: Int,
    val genres: List<String>,
    val status: String, // "Hoàn Tất" or "Đang Phát Sóng"
    val studio: String,
    val episodes: List<Episode> = emptyList(),
    val seasons: List<AnimeSeason> = emptyList(),
    val isFeatured: Boolean = false,
    val views: String = "1.2M",
    val section: String = "Mới Cập Nhật", // "Mới Cập Nhật", "Anime Bộ", "Anime Lẻ", "Xu Hướng Mùa Này"
    val nextEpisodeAirInfo: String? = null // e.g. "Tập 13 sẽ phát lúc 23:00 Thứ Năm hàng tuần"
)
