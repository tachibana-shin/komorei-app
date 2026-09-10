package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/**
 * Represents a genre category in Aidoku/Stremio discovery tab.
 */
@JsonClass(generateAdapter = true)
data class Genre(
    val id: String,
    val name: String,
    val emoji: String,
    val accentColorHex: Long,
    val count: Int
)
