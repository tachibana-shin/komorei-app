package git.shin.komorei.ui.components.search.filters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue

/**
 * Pure helper + draft-state primitives for the Aidoku-style DynamicFilters UI
 * (a port of Aidoku's `Filter` handling in `Aidoku/Features/Source/Filter`).
 *
 * The "resolved default" rules mirror Aidoku exactly:
 *  - sort     → `SortFilterDefault` (index 0, ascending false by default);
 *  - select   → `default ?: options[0]` (Aidoku's `resolvedDefaultValue`);
 *  - multiselect → `defaultIncluded`/`defaultExcluded` (`[]` by default);
 *  - check    → `true → 1`, `false → 2`, `null → 0` (tristate).
 * A filter value that equals its defaults is dropped from `enabledFilters`
 * (Aidoku `update*Filter()` semantics), so "default" never travels to the
 * source.
 */

/** Defaults of a sort filter — Aidoku `SortFilterDefault` (index 0 by default). */
data class SortFilterDefaults(val index: Int, val ascending: Boolean)

fun sortFilterDefaults(kind: FilterKind.Sort): SortFilterDefaults =
    kind.default?.let { SortFilterDefaults(it.index, it.ascending) } ?: SortFilterDefaults(0, false)

/** Default included/excluded options of a multi-select filter. */
data class MultiSelectFilterDefaults(val included: List<String>, val excluded: List<String>)

fun multiSelectFilterDefaults(kind: FilterKind.MultiSelect): MultiSelectFilterDefaults =
    MultiSelectFilterDefaults(kind.defaultIncluded ?: emptyList(), kind.defaultExcluded ?: emptyList())

/** The select option treated as the reset value (Aidoku `resolvedDefaultValue`). */
fun selectFilterDefaultValue(kind: FilterKind.Select): String =
    kind.default ?: kind.options.firstOrNull().orEmpty()

/**
 * Check tristate index from the filter's default boolean — Aidoku maps
 * `true → 1` (checked), `false → 2` (excluded), `null → 0` (off).
 */
fun checkFilterDefaultState(kind: FilterKind.Check): Int = when (kind.default) {
    true -> 1
    false -> 2
    null -> 0
}

/**
 * The header filter-count badge: every simple value counts 1 and a
 * multi-select counts its included + excluded options (Aidoku `filterCount`).
 */
fun activeFilterCount(enabled: List<FilterValue>): Int = enabled.sumOf {
    when (it) {
        is FilterValue.MultiSelect -> it.included.size + it.excluded.size
        else -> 1
    }
}

/**
 * Upserts [value] into a filter value list by id — `null` removes the id
 * (Aidoku `update*Filter` remove/replace/apppend behavior).
 */
fun upsertFilterValue(current: List<FilterValue>, id: String, value: FilterValue?): List<FilterValue> =
    current.filter { it.id != id } + listOfNotNull(value)

/** Builds the enabled value for a sort filter (`null` when it matches defaults). */
fun sortFilterValue(id: String, kind: FilterKind.Sort, index: Int, ascending: Boolean): FilterValue.Sort? {
    val d = sortFilterDefaults(kind)
    return if (index == d.index && ascending == d.ascending) null else FilterValue.Sort(id, index, ascending)
}

/** Builds the enabled value for a select filter (`null` when it matches the default). */
fun selectFilterValue(id: String, kind: FilterKind.Select, value: String): FilterValue.Select? =
    if (value == selectFilterDefaultValue(kind)) null else FilterValue.Select(id, value)

/** Builds the enabled value for a multi-select filter (`null` when it matches defaults). */
fun multiSelectFilterValue(
    id: String,
    kind: FilterKind.MultiSelect,
    included: List<String>,
    excluded: List<String>,
): FilterValue.MultiSelect? {
    val d = multiSelectFilterDefaults(kind)
    return if (included == d.included && excluded == d.excluded) {
        null
    } else {
        FilterValue.MultiSelect(id, included, excluded)
    }
}

/** Builds the enabled value for a check filter (`null` when state is 0/off). */
fun checkFilterValue(id: String, state: Int): FilterValue.Check? =
    if (state == 0) null else FilterValue.Check(id, state)

/** Builds the enabled value for a text filter (`null` when blank). */
fun textFilterValue(id: String, value: String): FilterValue.Text? =
    if (value.isBlank()) null else FilterValue.Text(id, value)

/** Builds the enabled value for a range filter (`null` when both ends are unset). */
fun rangeFilterValue(id: String, from: Float?, to: Float?): FilterValue.Range? =
    if (from == null && to == null) null else FilterValue.Range(id, from, to)

/**
 * Aidoku's tristate toggle for a multi-select option: included → (excluded if
 * `canExclude` else removed), excluded → removed, unset → included.
 */
fun toggleMultiSelectOption(
    included: List<String>,
    excluded: List<String>,
    canExclude: Boolean,
    option: String,
): Pair<List<String>, List<String>> = when {
    option in included -> {
        val nextIncluded = included - option
        if (canExclude) nextIncluded to (excluded + option) else nextIncluded to excluded
    }
    option in excluded -> included to (excluded - option)
    else -> (included + option) to excluded
}

/**
 * A mutable draft of the enabled filter values for the full filter sheet
 * (Aidoku `FilterListSheetView`'s `newEnabledFilters`). Values are committed
 * only when the sheet's "Áp dụng" is pressed — dismissing discards the draft.
 */
class FilterDraftState(initial: List<FilterValue>) {

    var values by mutableStateOf(initial)
        private set

    fun value(id: String): FilterValue? = values.firstOrNull { it.id == id }

    fun set(id: String, value: FilterValue?) {
        values = upsertFilterValue(values, id, value)
    }

    fun reset() {
        values = emptyList()
    }

    // ── typed accessors used by the group views ────────────────────────────

    fun sortState(filter: Filter): SortState {
        val kind = (filter.kind as? FilterKind.Sort) ?: return SortState()
        val d = sortFilterDefaults(kind)
        val v = value(filter.id) as? FilterValue.Sort
        return SortState(v?.index ?: d.index, v?.ascending ?: d.ascending)
    }

    fun selectState(filter: Filter): String {
        val kind = (filter.kind as? FilterKind.Select) ?: return ""
        val v = value(filter.id) as? FilterValue.Select
        return v?.value ?: selectFilterDefaultValue(kind)
    }

    fun multiSelectState(filter: Filter): MultiSelectState {
        val kind = (filter.kind as? FilterKind.MultiSelect) ?: return MultiSelectState()
        val d = multiSelectFilterDefaults(kind)
        val v = value(filter.id) as? FilterValue.MultiSelect
        return MultiSelectState(v?.included ?: d.included, v?.excluded ?: d.excluded)
    }

    fun checkState(filter: Filter): Int? {
        val v = value(filter.id) as? FilterValue.Check
        return v?.value
    }

    fun textState(filter: Filter): String = (value(filter.id) as? FilterValue.Text)?.value.orEmpty()

    fun rangeState(filter: Filter): RangeState {
        val v = value(filter.id) as? FilterValue.Range
        return RangeState(v?.from, v?.to)
    }
}

/** Snapshot of a sort filter's current selection. */
data class SortState(val index: Int = 0, val ascending: Boolean = false)

/** Snapshot of a multi-select filter's current selection. */
data class MultiSelectState(val included: List<String> = emptyList(), val excluded: List<String> = emptyList())

/** Snapshot of a range filter's current bounds. */
data class RangeState(val from: Float? = null, val to: Float? = null)