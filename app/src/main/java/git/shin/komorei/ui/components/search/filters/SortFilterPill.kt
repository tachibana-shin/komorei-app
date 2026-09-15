package git.shin.komorei.ui.components.search.filters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import git.shin.komorei.R
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue

/**
 * The sort filter pill (Aidoku `SortFilterView`): the label shows
 * "title: option" with the pill active when a non-default option (or
 * ascending flag) is applied. Tapping it opens a dropdown bottom sheet with
 * the [SortFilterGroup]; every change is committed to the filter list
 * immediately (Aidoku commits through the menu's binding).
 */
@Composable
fun SortFilterPill(
    filter: Filter,
    enabled: List<FilterValue>,
    onChange: (id: String, value: FilterValue?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = (filter.kind as? FilterKind.Sort) ?: return
    val defaults = sortFilterDefaults(kind)
    val current = enabled.firstOrNull { it.id == filter.id } as? FilterValue.Sort
    val selectedIndex = current?.index ?: defaults.index
    val ascending = current?.ascending ?: defaults.ascending
    val active = current != null

    val option = kind.options.getOrNull(selectedIndex)
    val label = if (option != null) {
        stringResource(R.string.filter_sort_label_format, filter.title.orEmpty(), option)
    } else {
        filter.title.orEmpty()
    }

    var showSheet by remember { mutableStateOf(false) }

    FilterPill(
        name = label,
        active = active,
        onClick = { showSheet = true },
        modifier = modifier,
    )

    if (showSheet) {
        FilterBottomSheet(
            title = filter.title.orEmpty(),
            onDismiss = { showSheet = false },
        ) {
            SortFilterGroup(
                kind = kind,
                selectedIndex = selectedIndex,
                ascending = ascending,
                onOptionChange = { index, asc ->
                    onChange(filter.id, sortFilterValue(filter.id, kind, index, asc))
                },
            )
        }
    }
}