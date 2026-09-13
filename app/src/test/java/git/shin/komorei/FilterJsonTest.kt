package git.shin.komorei

import com.squareup.moshi.Moshi
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterKindJsonAdapter
import git.shin.komorei.model.FilterValue
import git.shin.komorei.model.FilterValueJsonAdapter
import git.shin.komorei.model.SortFilterDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the Kotlin filter model to komorei-sdk's wire format
 * (crates/lib/src/structs/filter.rs): `"type"` discriminator + snake_case kind fields.
 */
class FilterJsonTest {

    private val moshi = Moshi.Builder()
        .add(FilterKind::class.java, FilterKindJsonAdapter())
        .add(FilterValue::class.java, FilterValueJsonAdapter())
        .build()

    private val filterAdapter = moshi.adapter(Filter::class.java)
    private val filterValueAdapter = moshi.adapter(FilterValue::class.java)

    @Test
    fun `Filter roundtrips all seven kinds`() {
        val filters = listOf(
            Filter("txt", "Từ khóa", kind = FilterKind.Text(placeholder = "Nhập tên...")),
            Filter(
                "srt",
                "Sắp xếp",
                kind = FilterKind.Sort(
                    canAscend = true,
                    options = listOf("Mới nhất", "Xem nhiều"),
                    default = SortFilterDefault(index = 1, ascending = false)
                )
            ),
            Filter("chk", "Phụ đề", kind = FilterKind.Check(name = "Vietsub", canExclude = true, default = true)),
            Filter(
                "sel",
                "Thể loại",
                kind = FilterKind.Select(
                    isGenre = true,
                    usesTagStyle = true,
                    options = listOf("Hành động", "Tình cảm"),
                    ids = listOf("action", "romance"),
                    default = "action"
                )
            ),
            Filter(
                "multi",
                "Tag",
                kind = FilterKind.MultiSelect(
                    isGenre = true,
                    canExclude = true,
                    usesTagStyle = true,
                    options = listOf("A", "B"),
                    defaultIncluded = listOf("A"),
                    defaultExcluded = listOf("B")
                )
            ),
            Filter("note1", null, kind = FilterKind.Note("Ghi chú từ source")),
            Filter("rng", "Năm", kind = FilterKind.Range(min = 2000f, max = 2026f, decimal = false))
        )

        filters.forEach { filter ->
            val json = filterAdapter.toJson(filter)
            assertEquals(filter, filterAdapter.fromJson(json))
        }
    }

    @Test
    fun `Filter wire format matches SDK discriminators and snake_case fields`() {
        val noteJson = filterAdapter.toJson(
            Filter("n", null, hideFromHeader = false, kind = FilterKind.Note("chú thích"))
        )
        assertTrue(noteJson.contains("\"type\":\"note\""))
        assertTrue(noteJson.contains("\"hide_from_header\":false"))
        assertTrue(noteJson.contains("\"text\":\"chú thích\""))

        val multiJson = filterAdapter.toJson(
            Filter(
                "m",
                "Tag",
                kind = FilterKind.MultiSelect(
                    isGenre = true,
                    canExclude = true,
                    usesTagStyle = true,
                    options = listOf("A", "B"),
                    defaultIncluded = listOf("A"),
                    defaultExcluded = listOf("B")
                )
            )
        )
        assertTrue(multiJson.contains("\"type\":\"multi-select\""))
        assertTrue(multiJson.contains("\"is_genre\":true"))
        assertTrue(multiJson.contains("\"can_exclude\":true"))
        assertTrue(multiJson.contains("\"uses_tag_style\":true"))
        assertTrue(multiJson.contains("\"default_included\":[\"A\"]"))
        assertTrue(multiJson.contains("\"default_excluded\":[\"B\"]"))

        val sortJson = filterAdapter.toJson(
            Filter(
                "s",
                "Sắp xếp",
                kind = FilterKind.Sort(
                    options = listOf("Mới nhất"),
                    default = SortFilterDefault(index = 0, ascending = true)
                )
            )
        )
        assertTrue(sortJson.contains("\"type\":\"sort\""))
        assertTrue(sortJson.contains("\"can_ascend\""))
        assertTrue(sortJson.contains("\"index\":0"))
    }

    @Test
    fun `FilterValue roundtrips all six kinds`() {
        val values = listOf<FilterValue>(
            FilterValue.Text(id = "txt", value = "onepiece"),
            FilterValue.Sort(id = "srt", index = 1, ascending = false),
            FilterValue.Check(id = "chk", value = 1),
            FilterValue.Select(id = "sel", value = "action"),
            FilterValue.MultiSelect(id = "multi", included = listOf("A"), excluded = listOf("B")),
            FilterValue.Range(id = "rng", from = 2000f, to = 2026f)
        )

        values.forEach { value ->
            val json = filterValueAdapter.toJson(value)
            assertEquals(value, filterValueAdapter.fromJson(json))
        }

        val multiJson = filterValueAdapter.toJson(FilterValue.MultiSelect("m", listOf("A"), listOf("B")))
        assertTrue(multiJson.contains("\"type\":\"multi-select\""))
    }
}