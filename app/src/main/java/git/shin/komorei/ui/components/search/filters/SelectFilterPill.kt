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
 * The single-select filter pill (Aidoku `SelectFilterView`): a pill opening
 * a dropdown bottom sheet with the [SelectFilterGroup]. Picking a non-default
 * option commits it immediately (and picking the default removes it again).
 */
@Composable
fun SelectFilterPill(
    filter: Filter,
    enabled: List<FilterValue>,
    onChange: (id: String, value: FilterValue?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = (filter.kind as? FilterKind.Select) ?: return
    val current = enabled.firstOrNull { it.id == filter.id } as? FilterValue.Select
    val selected = current?.value ?: selectFilterDefaultValue(kind)
    val active = current != null

    var showSheet by remember { mutableStateOf(false) }

    FilterPill(
        name = filter.title.orEmpty(),
        active = active,
        onClick = { showSheet = true },
        modifier = modifier,
    )

    if (showSheet) {
        FilterBottomSheet(
            title = filter.title.orEmpty(),
            onDismiss = { showSheet = false },
        ) {
            SelectFilterGroup(
                kind = kind,
                selected = selected,
                onSelect = { value ->
                    onChange(filter.id, selectFilterValue(filter.id, kind, value))
                },
            )
        }
    }
}