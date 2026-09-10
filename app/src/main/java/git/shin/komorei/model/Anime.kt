package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/**
 * Global status of an anime series.
 */
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
 * Represents a specific season or part of a franchise.
 * Note: Seasons are often separate anime IDs in many source extensions.
 */
@JsonClass(generateAdapter = true)
data class AnimeSeason(
    val animeId: String,
    val seasonNumber: Int,
    val title: String
)

/**
 * Interactive metadata link used for filtering (e.g., clicking a genre or studio).
 */
@JsonClass(generateAdapter = true)
data class CategoryLink(
    val name: String,
    val filters: List<SelectedFilter> = emptyList()
)

/**
 * Main Anime entity representing consolidated metadata.
 * Usually fetched in two stages: Lite (listing) and Full (details).
 */
@JsonClass(generateAdapter = true)
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
    val currentEpisode: String?, // e.g. "Ep 12/12", "HD Vietsub"
    val rating: Float?,
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
    val views: Int = 0,
    val nextEpisodeAirInfo: String? = null,
    val qualityTag: String? = "FHD"
)
