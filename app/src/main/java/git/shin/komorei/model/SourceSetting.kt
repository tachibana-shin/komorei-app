package git.shin.komorei.model

/**
 * One entry of a source's dynamic settings (`get_settings`), mirrored from the
 * runner's wire format for the app's settings screens.
 *
 * Rendered read-only today: the SDK exports `get_settings` but no
 * `update_setting`, so the values shown are the source's CURRENT defaults (the
 * source reads them itself through its `defaults` imports on the host).
 */
data class SourceSetting(
    val key: String,
    val title: String,
    val notification: String? = null,
    val requires: String? = null,
    val requiresFalse: String? = null,
    val refreshes: List<String>? = null,
    val value: SourceSettingValue,
)

/** The concrete kind of a [SourceSetting] — the fields needed for display. */
sealed interface SourceSettingValue {
    /** A section grouping settings — rendered as a header (footer) + children. */
    data class Group(val footer: String?, val items: List<SourceSetting>) : SourceSettingValue

    /** One-of-N selection. */
    data class Select(
        val values: List<String>,
        val titles: List<String>?,
        val default: String?,
    ) : SourceSettingValue

    /** Any-of-N selection. */
    data class MultiSelect(
        val values: List<String>,
        val titles: List<String>?,
        val default: List<String>?,
    ) : SourceSettingValue

    /** On/off switch. */
    data class Toggle(val subtitle: String?, val default: Boolean) : SourceSettingValue

    /** Numeric stepper. */
    data class Stepper(
        val minimumValue: Double,
        val maximumValue: Double,
        val stepValue: Double?,
        val default: Double?,
    ) : SourceSettingValue

    /** Segmented control. */
    data class Segment(val options: List<String>, val default: Int?) : SourceSettingValue

    /** Free text field. */
    data class Text(val placeholder: String?, val default: String?) : SourceSettingValue

    /** A one-shot action button (no-op in read-only mode). */
    data object Button : SourceSettingValue

    /** A link (could open an external browser). */
    data class Link(val url: String) : SourceSettingValue

    /** A nested page of settings. */
    data class Page(val items: List<SourceSetting>, val info: String?) : SourceSettingValue

    /** An editable list of strings. */
    data class EditableList(val placeholder: String?, val default: List<String>?) : SourceSettingValue

    /** A picker (like [Select] with titled options). */
    data class Picker(
        val values: List<String>,
        val titles: List<String>?,
        val default: String?,
    ) : SourceSettingValue

    /** A login flow (no-op read-only; shows the endpoint when present). */
    data class Login(val url: String?) : SourceSettingValue
}