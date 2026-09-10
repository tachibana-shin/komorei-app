package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/**
 * Represents an anime source provider (WASM or Script-based extension pattern).
 */
@JsonClass(generateAdapter = true)
data class Source(
    val id: String,
    val name: String,
    val icon: String = "",
    val version: String = "1.0.0",
    val baseUrl: String = "",
    val isEnabled: Boolean = true,
    val isAggregator: Boolean = false,
    val badgeColorHex: Long = 0xFFFF2A55
)
