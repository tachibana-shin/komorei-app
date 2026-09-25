package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/**
 * Represents a genre or category found in the discovery/hub tabs.
 */
@JsonClass(generateAdapter = true)
data class Genre(
    val id: String,
    val name: String,
    val emoji: String,
    val accentColorHex: Long,
    val count: Int,
)
