package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.model.Filter
import git.shin.komorei.model.FilterKind
import git.shin.komorei.ui.components.search.CompactInput
import git.shin.komorei.ui.theme.TextMuted

/**
 * A labeled text field for a `Text` filter (Aidoku `FilterListView`'s text
 * row — including the fake source's "Tìm kiếm" box). The value is trimmed
 * when committed; blank text removes the filter.
 *
 * A controlled component — the parent owns the string.
 */
@Composable
fun TextFilterRow(
    filter: Filter,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = (filter.kind as? FilterKind.Text) ?: return
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            text = filter.title.orEmpty(),
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
        CompactInput(
            value = value,
            onValueChange = onValueChange,
            hint = kind.placeholder.orEmpty(),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
