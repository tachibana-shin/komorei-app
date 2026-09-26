package git.shin.komorei.model

import androidx.room.ColumnInfo
import com.squareup.moshi.JsonClass

/**
 * Global status of an anime series.
 */
enum class AnimeStatus(
    val value: String,
) {
    ONGOING("ongoing"),
    COMPLETED("completed"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromString(value: String): AnimeStatus = entries.find { it.value == value.lowercase() } ?: UNKNOWN
    }
}

/**
 * Represents a specific season or part of a franchise.
 * Note: Seasons are often separate anime IDs in many source extensions.
 */
@JsonClass(generateAdapter = true)
data class AnimeSeason(
    val animeId: String,
    val title: String,
    /** Unique identity. Defaults to [animeId]; virtual 50-episode seasons override it. */
    val id: String = animeId,
)

/**
 * Interactive metadata link used for filtering (e.g., clicking a genre or studio).
 */
@JsonClass(generateAdapter = true)
data class CategoryLink(
    val name: String,
    val filters: List<FilterValue> = emptyList(),
)

/**
 * Main Anime entity representing consolidated metadata.
 * Usually fetched in two stages: Lite (listing) and Full (details).
 */
@JsonClass(generateAdapter = true)
data class Anime(
    val id: String,
    val sourceId: String,
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
    val qualityTag: String? = "FHD",
    /**
     * Source-defined extras, keyed by a dotted name (e.g. `"avs.recommendations"`).
     *
     * Data the source's own page carries but the app does not model, so a
     * source can hand it over instead of making the app ask for it again.
     * Values are opaque strings; a source storing anything structured encodes
     * it. Defaults to empty so a source that sets nothing costs nothing.
     *
     * The Room annotation is the one place this model names a storage detail,
     * and it is load-bearing: `anime_library` is an `@Embedded` copy of this
     * class, and a `NOT NULL` column cannot be added to a table that already has
     * rows without a default. Room validates the migrated column's default
     * against this one, so `MIGRATION_4_5`'s `DEFAULT '{}'` and this string have
     * to agree — including the quotes, which are part of the SQL literal.
     */
    @ColumnInfo(defaultValue = "'{}'")
    val extra: Map<String, String> = emptyMap(),
)
