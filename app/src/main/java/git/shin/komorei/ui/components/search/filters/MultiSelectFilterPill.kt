package git.shin.komorei.ui.components.search.filters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue

/**
 * The multi-select filter pill (Aidoku `MultiSelectFilterView`): a pill with
 * a count badge (included + excluded options, like Aidoku's badge) opening a
 * dropdown bottom sheet with the [MultiSelectFilterGroup]. Every toggle is
 * committed immediately; toggling back to the defaults removes the value.
 */
@Composable
fun MultiSelectFilterPill(
    filter: Filter,
    enabled: List<FilterValue>,
    onChange: (id: String, value: FilterValue?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = (filter.kind as? FilterKind.MultiSelect) ?: return
    val defaults = multiSelectFilterDefaults(kind)
    val current = enabled.firstOrNull { it.id == filter.id } as? FilterValue.MultiSelect
    val included = current?.included ?: defaults.included
    val excluded = current?.excluded ?: defaults.excluded
    val active = current != null

    var showSheet by remember { mutableStateOf(false) }

    FilterPill(
        name = filter.title.orEmpty(),
        active = active,
        onClick = { showSheet = true },
        badgeCount = included.size + excluded.size,
        modifier = modifier,
    )

    if (showSheet) {
        FilterBottomSheet(
            title = filter.title.orEmpty(),
            onDismiss = { showSheet = false },
        ) {
            MultiSelectFilterGroup(
                kind = kind,
                included = included,
                excluded = excluded,
                onToggle = { nextIncluded, nextExcluded ->
                    onChange(
                        filter.id,
                        multiSelectFilterValue(filter.id, kind, nextIncluded, nextExcluded),
                    )
                },
            )
        }
    }
}