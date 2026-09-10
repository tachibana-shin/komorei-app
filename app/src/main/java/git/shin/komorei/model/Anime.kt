package git.shin.komorei.model

enum class AnimeStatus(val value: String) {
    ONGOING("ongoing"),
    COMPLETED("completed"),
    UNKNOWN("unknown");

    companion object {
        fun fromString(value: String): AnimeStatus {
            return entries.find { it.value == value.lowercase() } ?: UNKNOWN
        }
    }
}

/**
 * Represents a season or part of an anime (e.g. Phần 1, Phần 2, OVA, Movie).
 * In many websites, seasons are different anime IDs linked together.
 */
data class AnimeSeason(
    val animeId: String,
    val seasonNumber: Int,
    val title: String,
    val episodes: List<Episode> = emptyList()
)

data class FilterOption(
    val id: String,
    val name: String
)

data class FilterGroup(
    val id: String,
    val name: String,
    val options: List<FilterOption>,
    val isMultiple: Boolean = false,
    val default: String? = null
)

data class SelectedFilter(
    val groupId: String,
    val id: String,
    val name: String,
    val include: Boolean = true,
    val exclude: Boolean = false
)

data class CategoryLink(
    val name: String,
    val filters: List<SelectedFilter> = emptyList()
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
    val currentEpisode: String?, // e.g. "Tập 12/12", "Tập 24 End", "HD Vietsub"
    val rating: Float?, // e.g. 4.9
    val ratingCount: Int?,
    val status: AnimeStatus,

    val releaseYear: CategoryLink?,
    val genres: List<CategoryLink>,
    val authors: List<CategoryLink>,
    val studio: CategoryLink?,
    val seasonOf: CategoryLink?,
    val countries: List<CategoryLink> = emptyList(),

    val episodes: List<Episode> = emptyList(),
    val seasons: List<AnimeSeason> = emptyList(),
    val isFeatured: Boolean = false,
    val views: Int = 1200000,
//    val section: String = "Mới Cập Nhật", // "Mới Cập Nhật", "Anime Bộ", "Anime Lẻ", "Xu Hướng Mùa Này" -- merged to genres
    val nextEpisodeAirInfo: String? = null, // e.g. "Tập 13 sẽ phát lúc 23:00 Thứ Năm hàng tuần"
    val qualityTag: String? = "FHD" // e.g. "FHD", "4K", "1080p", "BD"
)
