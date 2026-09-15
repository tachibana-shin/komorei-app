package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = kind.placeholder.orEmpty(),
                    color = TextMuted,
                    fontSize = 14.sp,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark,
                focusedBorderColor = AnimeRed,
                unfocusedBorderColor = CardBorderDark,
                cursorColor = AnimeRed,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}