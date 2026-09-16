package git.shin.komorei.ui.components.search.filters

import git.shin.komorei.ui.components.search.CompactInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

/**
 * A labeled from/to pair of numeric fields for a `Range` filter (Aidoku
 * `RangeFilterGroupView`): the bound is committed only when it parses and
 * sits inside `min..max` and `from ≤ to`, otherwise the fields turn red and
 * the previously committed value is kept (Aidoku only updates on success).
 *
 * A controlled component — the parent owns the last VALID bounds; the local
 * strings let the user type freely in between.
 */
@Composable
fun RangeFilterRow(
    filter: Filter,
    from: Float?,
    to: Float?,
    onBoundsChange: (from: Float?, to: Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = (filter.kind as? FilterKind.Range) ?: return

    var fromText by remember { mutableStateOf(from?.let { formatNumber(it) }.orEmpty()) }
    var toText by remember { mutableStateOf(to?.let { formatNumber(it) }.orEmpty()) }

    val min = kind.min
    val max = kind.max

    val parsedFrom = fromText.toFloatOrNull()
    val parsedTo = toText.toFloatOrNull()
    val fromError = parsedFrom != null && (
        (min != null && parsedFrom < min) ||
            (parsedTo != null && parsedFrom > parsedTo)
        )
    val toError = parsedTo != null && (
        (max != null && parsedTo > max) ||
            (parsedFrom != null && parsedTo < parsedFrom)
        )
    val hasError = fromError || toError

    fun commit() {
        val nextFrom = fromText.toFloatOrNull()
        val nextTo = toText.toFloatOrNull()
        val nextFromInRange = nextFrom == null ||
            (min == null || nextFrom >= min) &&
            (nextTo == null || nextFrom <= nextTo)
        val nextToInRange = nextTo == null ||
            (max == null || nextTo <= max) &&
            (nextFrom == null || nextTo >= nextFrom)
        if (nextFromInRange && nextToInRange) {
            onBoundsChange(nextFrom, nextTo)
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            text = filter.title.orEmpty(),
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            RangeField(
                label = stringResource(R.string.filter_range_from),
                text = fromText,
                onTextChange = { fromText = it; commit() },
                error = fromError || hasError,
                decimal = kind.decimal,
                modifier = Modifier.weight(1f),
            )
            RangeField(
                label = stringResource(R.string.filter_range_to),
                text = toText,
                onTextChange = { toText = it; commit() },
                error = toError || hasError,
                decimal = kind.decimal,
                modifier = Modifier.weight(1f),
            )
        }
        if (hasError) {
            Text(
                text = stringResource(R.string.filter_range_error),
                color = AnimeRed,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RangeField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    error: Boolean,
    decimal: Boolean,
    modifier: Modifier = Modifier,
) {
    CompactInput(
        value = text,
        onValueChange = onTextChange,
        hint = label,
        isError = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        cursorColor = AnimeRed,
        modifier = modifier,
    )
}

/** Formats a numeric bound for display (ints stay whole). */
private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()