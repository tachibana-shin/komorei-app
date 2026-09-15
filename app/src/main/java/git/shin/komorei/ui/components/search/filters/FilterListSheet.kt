package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * Edits land in a local [FilterDraftState] and are committed ONLY on
 * "Áp dụng" (dismissing or "Hủy" discards them); "Đặt lại" clears all
 * filters and applies immediately (Aidoku resets + dismisses).
 */
@Composable
fun FilterListSheet(
    filters: List<Filter>,
    initialEnabled: List<FilterValue>,
    onApply: (List<FilterValue>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = remember { FilterDraftState(initialEnabled) }

    FilterBottomSheet(
        title = stringResource(R.string.filter_sheet_title),
        onDismiss = onDismiss,
        onReset = {
            draft.reset()
            onApply(emptyList())
            onDismiss()
        },
        onCancel = onDismiss,
        onApply = {
            onApply(draft.values)
            onDismiss()
        },
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
                            value = draft.textState(filter),
                            onValueChange = { value ->
                                draft.set(filter.id, textFilterValue(filter.id, value))
                            },
                        )
                        is FilterKind.Sort -> {
                            val s = draft.sortState(filter)
                            FilterGroupHeader(title = filter.title.orEmpty())
                            SortFilterGroup(
                                kind = kind,
                                selectedIndex = s.index,
                                ascending = s.ascending,
                                onOptionChange = { index, asc ->
                                    draft.set(filter.id, sortFilterValue(filter.id, kind, index, asc))
                                },
                            )
                        }
                        is FilterKind.Check -> {
                            val state = draft.checkState(filter) ?: checkFilterDefaultState(kind)
                            FilterGroupHeader(title = filter.title.orEmpty())
                            CheckFilterGroup(
                                filter = filter,
                                state = state,
                                onStateChange = { next ->
                                    draft.set(filter.id, checkFilterValue(filter.id, next))
                                },
                            )
                        }
                        is FilterKind.Select -> {
                            FilterGroupHeader(title = filter.title.orEmpty())
                            SelectFilterGroup(
                                kind = kind,
                                selected = draft.selectState(filter),
                                onSelect = { value ->
                                    draft.set(filter.id, selectFilterValue(filter.id, kind, value))
                                },
                            )
                        }
                        is FilterKind.MultiSelect -> {
                            val m = draft.multiSelectState(filter)
                            FilterGroupHeader(title = filter.title.orEmpty())
                            MultiSelectFilterGroup(
                                kind = kind,
                                included = m.included,
                                excluded = m.excluded,
                                onToggle = { nextIncluded, nextExcluded ->
                                    draft.set(
                                        filter.id,
                                        multiSelectFilterValue(filter.id, kind, nextIncluded, nextExcluded),
                                    )
                                },
                            )
                        }
                        is FilterKind.Range -> RangeFilterRow(
                            filter = filter,
                            from = draft.rangeState(filter).from,
                            to = draft.rangeState(filter).to,
                            onBoundsChange = { from, to ->
                                draft.set(filter.id, rangeFilterValue(filter.id, from, to))
                            },
                        )
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