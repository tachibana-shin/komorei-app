package git.shin.komorei.ui.components.search.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.ui.components.rememberSystemNavigationBarBottom
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

/**
 * Shared chrome for the filter bottom sheets: a [ModalBottomSheet] with a
 * [title] header (a leading dismiss X, an optional trailing "Đặt lại" action).
 *
 * All filter sheets are footer-less — changes commit instantly when a
 * value is toggled/selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // The sheet's Dialog window does not reliably receive system-bar insets (zero
    // on several devices/emulators), so the ModalBottomSheet default padding is
    // often a no-op. Read the navigation-bar height from the HOST window here and
    // pad the content explicitly so the last row never hides behind the nav bar.
    val navBarBottom = rememberSystemNavigationBarBottom()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        dragHandle = null,
    ) {
        Column(modifier = modifier.fillMaxWidth().padding(bottom = navBarBottom + 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).testTag("filter_sheet_title"),
                )
                if (onReset != null) {
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier
                            // TV focus highlight (no-op on phones).
                            .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)
                            .testTag("filter_reset_button"),
                    ) {
                        Text(stringResource(R.string.filter_reset), color = TextMuted)
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        // TV focus highlight (no-op on phones).
                        .tvFocus(shape = CircleShape, scale = 1.15f)
                        .testTag("filter_sheet_close"),
                ) {
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
        }
    }
}