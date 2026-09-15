package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue

/**
 * The Aidoku `FilterHeaderView` port for the per-source search screen — a
 * sticky, horizontally scrollable row of the aggregate filter sheet button
 * followed by one pill per header-eligible filter, in Aidoku's order
 * (enabled filters first, then the rest).
 *
 * Header eligibility (Aidoku `FilterHeaderView`):
 *  - `hideFromHeader` filters are never shown;
 *  - Text / Note / Range filters have no header widget;
 *  - Check filters are shown only when they have no default (a tristate pill
 *    with a default is pointless in the header).
 *
 * Every pill commits through [onFilterValueChange] immediately.
 */
@Composable
fun FilterHeaderRow(
    filters: List<Filter>,
    enabledFilters: List<FilterValue>,
    onFilterValueChange: (id: String, value: FilterValue?) -> Unit,
    onOpenFilterSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledIds = enabledFilters.mapTo(mutableSetOf()) { it.id }
    val headerFilters = filters
        .filter { it.isHeaderEligible() }
        .sortedBy { if (it.id in enabledIds) 0 else 1 }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        item(key = "_sheet") {
            FilterSheetButton(
                enabledCount = activeFilterCount(enabledFilters),
                onClick = onOpenFilterSheet,
            )
        }
        items(headerFilters, key = { "filter_${it.id}" }) { filter ->
            when (val kind = filter.kind) {
                is FilterKind.Sort -> SortFilterPill(
                    filter = filter,
                    enabled = enabledFilters,
                    onChange = onFilterValueChange,
                )
                is FilterKind.Select -> SelectFilterPill(
                    filter = filter,
                    enabled = enabledFilters,
                    onChange = onFilterValueChange,
                )
                is FilterKind.MultiSelect -> MultiSelectFilterPill(
                    filter = filter,
                    enabled = enabledFilters,
                    onChange = onFilterValueChange,
                )
                is FilterKind.Check -> CheckFilterPill(
                    filter = filter,
                    enabled = enabledFilters,
                    onChange = onFilterValueChange,
                )
                else -> Unit
            }
        }
    }
}

/** Aidoku `FilterHeaderView` eligibility rule for a filter. */
private fun Filter.isHeaderEligible(): Boolean {
    if (hideFromHeader == true) return false
    return when (kind) {
        is FilterKind.Sort,
        is FilterKind.Select,
        is FilterKind.MultiSelect,
        -> true
        is FilterKind.Check -> kind.default == null
        else -> false
    }
}