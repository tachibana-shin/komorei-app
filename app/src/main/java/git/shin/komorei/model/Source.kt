package git.shin.komorei.model

import com.squareup.moshi.JsonClass

/**
 * Represents an anime source provider (WASM or Script-based extension pattern).
 *
 * [isEnabled] is the *desired* state applied by the source manager; disabled
 * sources stay installed (app model already carries `languages`/`contentRating`
 * from the `.krx` manifest so the Sources tab can render wording/badges).
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
    val badgeColorHex: Long = 0xFFFF2A55,
    val languages: List<String> = emptyList(),
    val contentRating: Int = 0,
)
