package git.shin.komorei.ui.components.search.filters

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue

/**
 * The tristate checkbox pill (Aidoku `CheckFilterView`): a direct button — no
 * dropdown — that cycles 0 (off) → 1 (✓ on) → 2 (✗ excluded when
 * `canExclude`) → 0 on each tap and commits immediately.
 */
@Composable
fun CheckFilterPill(
    filter: Filter,
    enabled: List<FilterValue>,
    onChange: (id: String, value: FilterValue?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = (filter.kind as? FilterKind.Check) ?: return
    val current = enabled.firstOrNull { it.id == filter.id } as? FilterValue.Check
    val state = current?.value ?: 0
    val active = state != 0
    val name = kind.name ?: filter.title.orEmpty()

    FilterPill(
        name = name,
        active = active,
        onClick = {
            onChange(filter.id, checkFilterValue(filter.id, nextCheckState(state, kind.canExclude)))
        },
        chevron = false,
        icon = when (state) {
            1 -> Icons.Default.Check
            2 -> Icons.Default.Close
            else -> null
        },
        modifier = modifier,
    )
}