package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

/**
 * Shared chrome for the filter bottom sheets: a [ModalBottomSheet] with a
 * [title] header (a leading dismiss X, an optional trailing "Đặt lại" action)
 * and an optional footer action bar (Hủy / Áp dụng).
 *
 * The pill dropdown sheets use it footer-less; the aggregate filter sheet
 * passes [onReset]/[onCancel]/[onApply].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onApply: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = null,
    ) {
        Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (onReset != null) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.filter_reset), color = TextMuted)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.filter_cancel),
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
            content()
            if (onCancel != null || onApply != null) {
                HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    if (onCancel != null) {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.filter_cancel), color = TextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (onApply != null) {
                        TextButton(onClick = onApply) {
                            Text(stringResource(R.string.filter_apply), color = AnimeRed)
                        }
                    }
                }
            }
        }
    }
}