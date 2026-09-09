package git.shin.komorei.model

/**
 * Represents a genre category in Aidoku/Stremio discovery tab.
 */
data class Genre(
    val id: String,
    val name: String,
    val emoji: String,
    val accentColorHex: Long,
    val count: Int
)
