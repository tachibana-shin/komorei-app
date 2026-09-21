package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.model.FilterValue
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

/**
 * The aggregate filter bottom sheet (Aidoku `FilterListSheetView` + the
 * per-kind `Filter*GroupView`s): EVERY filter of the source in a scrollable
 * list — text fields, sort chips, checkboxes, selects, multi-selects, range
 * bounds and note blocks.
 *
 * Changes are applied IMMEDIATELY (no draft, no "Áp dụng"/"Hủy" buttons).
 * "Đặt lại" clears all filters and applies immediately.
 */
@Composable
fun FilterListSheet(
    filters: List<Filter>,
    initialEnabled: List<FilterValue>,
    onApply: (List<FilterValue>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var enabledFilters by remember { mutableStateOf(initialEnabled) }

    /** Applies the current filter list and optionally dismisses. */
    fun applyAndDismiss(values: List<FilterValue>, dismiss: Boolean = false) {
        enabledFilters = values
        onApply(values)
        if (dismiss) onDismiss()
    }

    FilterBottomSheet(
        title = stringResource(R.string.filter_sheet_title),
        onDismiss = onDismiss,
        onReset = {
            applyAndDismiss(emptyList(), dismiss = true)
        },
        // Changes commit instantly — no footer buttons
        modifier = modifier,
    ) {
        if (filters.isEmpty()) {
            Text(
                text = stringResource(R.string.filter_none),
                color = TextMuted,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                filters.forEach { filter ->
                    when (val kind = filter.kind) {
                        is FilterKind.Text -> TextFilterRow(
                            filter = filter,
                            value = (enabledFilters.firstOrNull { it.id == filter.id } as? FilterValue.Text)?.value.orEmpty(),
                            onValueChange = { value ->
                                val newValue = textFilterValue(filter.id, value)
                                applyAndDismiss(upsertFilterValue(enabledFilters, filter.id, newValue))
                            },
                        )
                        is FilterKind.Sort -> {
                            val current = enabledFilters.firstOrNull { it.id == filter.id } as? FilterValue.Sort
                            val d = sortFilterDefaults(kind)
                            val index = current?.index ?: d.index
                            val ascending = current?.ascending ?: d.ascending
                            FilterGroupHeader(title = filter.title.orEmpty())
                            SortFilterGroup(
                                kind = kind,
                                selectedIndex = index,
                                ascending = ascending,
                                onOptionChange = { newIndex, newAscending ->
                                    val newValue = sortFilterValue(filter.id, kind, newIndex, newAscending)
                                    applyAndDismiss(upsertFilterValue(enabledFilters, filter.id, newValue))
                                },
                            )
                        }
                        is FilterKind.Check -> {
                            val state = (enabledFilters.firstOrNull { it.id == filter.id } as? FilterValue.Check)?.value ?: checkFilterDefaultState(kind)
                            FilterGroupHeader(title = filter.title.orEmpty())
                            CheckFilterGroup(
                                filter = filter,
                                state = state,
                                onStateChange = { next ->
                                    val newValue = checkFilterValue(filter.id, next)
                                    applyAndDismiss(upsertFilterValue(enabledFilters, filter.id, newValue))
                                },
                            )
                        }
                        is FilterKind.Select -> {
                            val selected = (enabledFilters.firstOrNull { it.id == filter.id } as? FilterValue.Select)?.value ?: selectFilterDefaultValue(kind)
                            FilterGroupHeader(title = filter.title.orEmpty())
                            SelectFilterGroup(
                                kind = kind,
                                selected = selected,
                                onSelect = { value ->
                                    val newValue = selectFilterValue(filter.id, kind, value)
                                    applyAndDismiss(upsertFilterValue(enabledFilters, filter.id, newValue))
                                },
                            )
                        }
                        is FilterKind.MultiSelect -> {
                            val current = enabledFilters.firstOrNull { it.id == filter.id } as? FilterValue.MultiSelect
                            val d = multiSelectFilterDefaults(kind)
                            val included = current?.included ?: d.included
                            val excluded = current?.excluded ?: d.excluded
                            FilterGroupHeader(title = filter.title.orEmpty())
                            MultiSelectFilterGroup(
                                kind = kind,
                                included = included,
                                excluded = excluded,
                                onToggle = { nextIncluded, nextExcluded ->
                                    val newValue = multiSelectFilterValue(filter.id, kind, nextIncluded, nextExcluded)
                                    applyAndDismiss(upsertFilterValue(enabledFilters, filter.id, newValue))
                                },
                            )
                        }
                        is FilterKind.Range -> {
                            val current = enabledFilters.firstOrNull { it.id == filter.id } as? FilterValue.Range
                            val from = current?.from
                            val to = current?.to
                            RangeFilterRow(
                                filter = filter,
                                from = from,
                                to = to,
                                onBoundsChange = { newFrom, newTo ->
                                    val newValue = rangeFilterValue(filter.id, newFrom, newTo)
                                    applyAndDismiss(upsertFilterValue(enabledFilters, filter.id, newValue))
                                },
                            )
                        }
                        is FilterKind.Note -> FilterGroupHeader(
                            title = kind.text,
                            muted = true,
                        )
                    }
                }
            }
        }
    }
}

/** A small section header above each filter block in the list sheet. */
@Composable
private fun FilterGroupHeader(title: String, muted: Boolean = false) {
    Text(
        text = title,
        color = if (muted) TextMuted else TextPrimary,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
    )
}