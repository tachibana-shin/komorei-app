package git.shin.komorei.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/**
 * Wire value of [FilterKind.MultiSelect] in `source.json`. Shared by the parser
 * and both Moshi adapters so the three can never drift apart.
 */
private const val TYPE_MULTI_SELECT = "multi-select"

/**
 * A filter that a source exposes for search.
 *
 * Mirrors `Filter` in komorei-sdk (`crates/lib/src/structs/filter.rs`): same fields and the
 * same wire format (`"type"` discriminator + kind-specific fields), so data coming from the
 * SDK runtime (Phase 4) decodes 1:1.
 */
@JsonClass(generateAdapter = true)
data class Filter(
    val id: String,
    val title: String? = null,
    @Json(name = "hide_from_header") val hideFromHeader: Boolean? = null,
    val kind: FilterKind,
)

/**
 * A default value for a sort filter.
 *
 * Mirrors `SortFilterDefault` in komorei-sdk.
 */
@JsonClass(generateAdapter = true)
data class SortFilterDefault(
    val index: Int,
    val ascending: Boolean,
)

/**
 * The kind of a search filter.
 *
 * Mirrors `FilterKind` in komorei-sdk — 7 kinds: text, sort, check, select, multi-select, note, range.
 */
sealed class FilterKind {
    /** A text field. */
    data class Text(
        val placeholder: String? = null,
    ) : FilterKind()

    /** A list of sort options. */
    data class Sort(
        @Json(name = "can_ascend") val canAscend: Boolean = true,
        val options: List<String> = emptyList(),
        val default: SortFilterDefault? = null,
    ) : FilterKind()

    /** A checkbox (tristate when [canExclude]). */
    data class Check(
        val name: String? = null,
        @Json(name = "can_exclude") val canExclude: Boolean = false,
        val default: Boolean? = null,
    ) : FilterKind()

    /** A list of values that allows a single selection. */
    data class Select(
        @Json(name = "is_genre") val isGenre: Boolean = false,
        @Json(name = "uses_tag_style") val usesTagStyle: Boolean = false,
        val options: List<String> = emptyList(),
        val ids: List<String>? = null,
        val default: String? = null,
    ) : FilterKind()

    /** A list of values that allows multiple selections. */
    data class MultiSelect(
        @Json(name = "is_genre") val isGenre: Boolean = false,
        @Json(name = "can_exclude") val canExclude: Boolean = false,
        @Json(name = "uses_tag_style") val usesTagStyle: Boolean = false,
        val options: List<String> = emptyList(),
        val ids: List<String>? = null,
        @Json(name = "default_included") val defaultIncluded: List<String>? = null,
        @Json(name = "default_excluded") val defaultExcluded: List<String>? = null,
    ) : FilterKind()

    /** A block of text displayed in the filter menu. */
    data class Note(
        val text: String,
    ) : FilterKind()

    /** A range filter. */
    data class Range(
        val min: Float? = null,
        val max: Float? = null,
        val decimal: Boolean = false,
    ) : FilterKind()
}

/**
 * A configured filter value applied by the user and sent back to the source on search.
 *
 * Mirrors `FilterValue` in komorei-sdk.
 */
sealed class FilterValue {
    /** The filter id this value belongs to. */
    abstract val id: String

    /** A string from a text field. */
    data class Text(
        override val id: String,
        val value: String,
    ) : FilterValue()

    /** A value from a sort filter. */
    data class Sort(
        override val id: String,
        val index: Int,
        val ascending: Boolean,
    ) : FilterValue()

    /** A value from a check filter. */
    data class Check(
        override val id: String,
        val value: Int,
    ) : FilterValue()

    /** A value from a select filter. */
    data class Select(
        override val id: String,
        val value: String,
    ) : FilterValue()

    /** A list of values from a multi-select filter. */
    data class MultiSelect(
        override val id: String,
        val included: List<String>,
        val excluded: List<String>,
    ) : FilterValue()

    /** A range of values from a range filter. */
    data class Range(
        override val id: String,
        val from: Float?,
        val to: Float?,
    ) : FilterValue()
}

/**
 * Polymorphic JSON adapter for [FilterKind].
 *
 * Same wire format as komorei-sdk's `Filter` serialization: a `"type"` discriminator
 * (`text`/`sort`/`check`/`select`/`multi-select`/`note`/`range`) plus kind-specific fields.
 */
class FilterKindJsonAdapter : JsonAdapter<FilterKind>() {
    override fun fromJson(reader: JsonReader): FilterKind {
        reader.beginObject()
        val fields = LinkedHashMap<String, Any?>()
        while (reader.hasNext()) {
            fields[reader.nextName()] = readValue(reader)
        }
        reader.endObject()

        val type = fields["type"] as? String
        return when (type) {
            "text" -> FilterKind.Text(placeholder = fields["placeholder"] as? String)
            "sort" ->
                FilterKind.Sort(
                    canAscend = fields["can_ascend"] as? Boolean ?: true,
                    options = (fields["options"] as? List<*>)?.castStrings() ?: emptyList(),
                    default = (fields["default"] as? Map<*, *>)?.toSortFilterDefault(),
                )
            "check" ->
                FilterKind.Check(
                    name = fields["name"] as? String,
                    canExclude = fields["can_exclude"] as? Boolean ?: false,
                    default = fields["default"] as? Boolean,
                )
            "select" ->
                FilterKind.Select(
                    isGenre = fields["is_genre"] as? Boolean ?: false,
                    usesTagStyle = fields["uses_tag_style"] as? Boolean ?: false,
                    options = (fields["options"] as? List<*>)?.castStrings() ?: emptyList(),
                    ids = (fields["ids"] as? List<*>)?.castStrings(),
                    default = fields["default"] as? String,
                )
            TYPE_MULTI_SELECT ->
                FilterKind.MultiSelect(
                    isGenre = fields["is_genre"] as? Boolean ?: false,
                    canExclude = fields["can_exclude"] as? Boolean ?: false,
                    usesTagStyle = fields["uses_tag_style"] as? Boolean ?: false,
                    options = (fields["options"] as? List<*>)?.castStrings() ?: emptyList(),
                    ids = (fields["ids"] as? List<*>)?.castStrings(),
                    defaultIncluded = (fields["default_included"] as? List<*>)?.castStrings(),
                    defaultExcluded = (fields["default_excluded"] as? List<*>)?.castStrings(),
                )
            "note" -> FilterKind.Note(text = fields["text"] as? String ?: "")
            "range" ->
                FilterKind.Range(
                    min = (fields["min"] as? Double)?.toFloat(),
                    max = (fields["max"] as? Double)?.toFloat(),
                    decimal = fields["decimal"] as? Boolean ?: false,
                )
            else -> throw JsonDataException("Unknown filter kind: $type")
        }
    }

    override fun toJson(
        writer: JsonWriter,
        value: FilterKind?,
    ) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        when (value) {
            is FilterKind.Text -> {
                writer.name("type").value("text")
                writer.name("placeholder").value(value.placeholder)
            }
            is FilterKind.Sort -> {
                writer.name("type").value("sort")
                writer.name("can_ascend").value(value.canAscend)
                writer.stringList("options", value.options)
                writer.name("default")
                val default = value.default
                if (default == null) {
                    writer.nullValue()
                } else {
                    writer.beginObject()
                    writer.name("index").value(default.index)
                    writer.name("ascending").value(default.ascending)
                    writer.endObject()
                }
            }
            is FilterKind.Check -> {
                writer.name("type").value("check")
                writer.name("name").value(value.name)
                writer.name("can_exclude").value(value.canExclude)
                writer.name("default").value(value.default)
            }
            is FilterKind.Select -> {
                writer.name("type").value("select")
                writer.name("is_genre").value(value.isGenre)
                writer.name("uses_tag_style").value(value.usesTagStyle)
                writer.stringList("options", value.options)
                writer.stringListOrNull("ids", value.ids)
                writer.name("default").value(value.default)
            }
            is FilterKind.MultiSelect -> {
                writer.name("type").value(TYPE_MULTI_SELECT)
                writer.name("is_genre").value(value.isGenre)
                writer.name("can_exclude").value(value.canExclude)
                writer.name("uses_tag_style").value(value.usesTagStyle)
                writer.stringList("options", value.options)
                writer.stringListOrNull("ids", value.ids)
                writer.stringListOrNull("default_included", value.defaultIncluded)
                writer.stringListOrNull("default_excluded", value.defaultExcluded)
            }
            is FilterKind.Note -> {
                writer.name("type").value("note")
                writer.name("text").value(value.text)
            }
            is FilterKind.Range -> {
                writer.name("type").value("range")
                writer.name("min").value(value.min)
                writer.name("max").value(value.max)
                writer.name("decimal").value(value.decimal)
            }
        }
        writer.endObject()
    }
}

/**
 * Polymorphic JSON adapter for [FilterValue].
 *
 * Same discriminator names as komorei-sdk's `FilterValue` enum variants
 * (`text`/`sort`/`check`/`select`/`multi-select`/`range`).
 */
class FilterValueJsonAdapter : JsonAdapter<FilterValue>() {
    override fun fromJson(reader: JsonReader): FilterValue {
        reader.beginObject()
        val fields = LinkedHashMap<String, Any?>()
        while (reader.hasNext()) {
            fields[reader.nextName()] = readValue(reader)
        }
        reader.endObject()

        val type = fields["type"] as? String
        return when (type) {
            "text" ->
                FilterValue.Text(
                    id = fields["id"] as? String ?: "",
                    value = fields["value"] as? String ?: "",
                )
            "sort" ->
                FilterValue.Sort(
                    id = fields["id"] as? String ?: "",
                    index = (fields["index"] as? Double)?.toInt() ?: 0,
                    ascending = fields["ascending"] as? Boolean ?: false,
                )
            "check" ->
                FilterValue.Check(
                    id = fields["id"] as? String ?: "",
                    value = (fields["value"] as? Double)?.toInt() ?: 0,
                )
            "select" ->
                FilterValue.Select(
                    id = fields["id"] as? String ?: "",
                    value = fields["value"] as? String ?: "",
                )
            TYPE_MULTI_SELECT ->
                FilterValue.MultiSelect(
                    id = fields["id"] as? String ?: "",
                    included = (fields["included"] as? List<*>)?.castStrings() ?: emptyList(),
                    excluded = (fields["excluded"] as? List<*>)?.castStrings() ?: emptyList(),
                )
            "range" ->
                FilterValue.Range(
                    id = fields["id"] as? String ?: "",
                    from = (fields["from"] as? Double)?.toFloat(),
                    to = (fields["to"] as? Double)?.toFloat(),
                )
            else -> throw JsonDataException("Unknown filter value type: $type")
        }
    }

    override fun toJson(
        writer: JsonWriter,
        value: FilterValue?,
    ) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        when (value) {
            is FilterValue.Text -> {
                writer.name("type").value("text")
                writer.name("id").value(value.id)
                writer.name("value").value(value.value)
            }
            is FilterValue.Sort -> {
                writer.name("type").value("sort")
                writer.name("id").value(value.id)
                writer.name("index").value(value.index)
                writer.name("ascending").value(value.ascending)
            }
            is FilterValue.Check -> {
                writer.name("type").value("check")
                writer.name("id").value(value.id)
                writer.name("value").value(value.value)
            }
            is FilterValue.Select -> {
                writer.name("type").value("select")
                writer.name("id").value(value.id)
                writer.name("value").value(value.value)
            }
            is FilterValue.MultiSelect -> {
                writer.name("type").value(TYPE_MULTI_SELECT)
                writer.name("id").value(value.id)
                writer.stringList("included", value.included)
                writer.stringList("excluded", value.excluded)
            }
            is FilterValue.Range -> {
                writer.name("type").value("range")
                writer.name("id").value(value.id)
                writer.name("from").value(value.from)
                writer.name("to").value(value.to)
            }
        }
        writer.endObject()
    }
}

/** Reads any JSON value into Kotlin primitives/collections (numbers as [Double]). */
private fun readValue(reader: JsonReader): Any? =
    when (reader.peek()) {
        JsonReader.Token.NULL -> reader.nextNull()
        JsonReader.Token.BOOLEAN -> reader.nextBoolean()
        JsonReader.Token.STRING -> reader.nextString()
        JsonReader.Token.NUMBER -> reader.nextDouble()
        JsonReader.Token.BEGIN_ARRAY -> {
            reader.beginArray()
            val list = ArrayList<Any?>()
            while (reader.hasNext()) list.add(readValue(reader))
            reader.endArray()
            list
        }
        JsonReader.Token.BEGIN_OBJECT -> {
            reader.beginObject()
            val map = LinkedHashMap<String, Any?>()
            while (reader.hasNext()) map[reader.nextName()] = readValue(reader)
            reader.endObject()
            map
        }
        else -> {
            reader.skipValue()
            null
        }
    }

private fun List<*>.castStrings(): List<String> = mapNotNull { it as? String }

private fun Map<*, *>.toSortFilterDefault(): SortFilterDefault? {
    val index = this["index"] as? Double ?: return null
    return SortFilterDefault(
        index = index.toInt(),
        ascending = this["ascending"] as? Boolean ?: false,
    )
}

private fun JsonWriter.stringList(
    name: String,
    value: List<String>,
) {
    this.name(name).beginArray()
    value.forEach { this.value(it) }
    this.endArray()
}

private fun JsonWriter.stringListOrNull(
    name: String,
    value: List<String>?,
) {
    this.name(name)
    if (value == null) {
        this.nullValue()
    } else {
        this.beginArray()
        value.forEach { this.value(it) }
        this.endArray()
    }
}
