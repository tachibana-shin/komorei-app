package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/**
 * Configuration for a specific filter group (e.g. "Sort by", "Year").
 */
@JsonClass(generateAdapter = true)
data class FilterGroup(
    val id: String,
    val name: String,
    val options: List<FilterOption>,
    val isMultiple: Boolean = false,
    val default: String? = null
)

/**
 * A single option within a [FilterGroup].
 */
@JsonClass(generateAdapter = true)
data class FilterOption(
    val id: String,
    val name: String
)

/**
 * An active filter state applied by the user.
 */
@JsonClass(generateAdapter = true)
data class SelectedFilter(
    val groupId: String,
    val id: String,
    val name: String,
    val include: Boolean = true,
    val exclude: Boolean = false
)
