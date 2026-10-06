package git.shin.komorei.ui.screens.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jeziellago.compose.markdowntext.MarkdownText
import git.shin.komorei.R
import git.shin.komorei.data.update.UpdateInfo
import git.shin.komorei.data.update.UpdateUiState
import git.shin.komorei.ui.theme.AnimeBlue
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus

/**
 * Announces a new release: what version, what changed, and the two answers.
 *
 * A sheet rather than a dialog, because the changelog is the content here and
 * that content is a scrollable column of headings and bullets. A dialog sizes
 * itself to its text and ends up either truncated behind an ellipsis or taller
 * than the screen; a sheet has a height budget and a scroll position, so the
 * notes can be read in full and still leave the buttons reachable.
 *
 * The notes are rendered as Markdown, which is what they are: the release
 * workflow publishes them through semantic-release, so they arrive already
 * carrying headings, bullets and links. Showing them as raw text meant the
 * reader saw `* **fixes:** something ([abc1234](https://…))`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Both states that mean there is something to show. `Downloading` is not
    // folded into `Available` by accident: it has to keep rendering after the
    // install is tapped, or the reader taps "update" and the sheet vanishes
    // mid-download with no progress and no way back to the app it left.
    val available =
        when (state) {
            is UpdateUiState.Available -> state.info
            is UpdateUiState.Downloading -> state.info
            else -> return
        }
    val downloading = state as? UpdateUiState.Downloading

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Bounded so the buttons stay reachable no matter how long the changelog is.
    // `fill = false` on the scroll column below is what lets it shrink: a
    // `fillMaxSize` child inside this Column would take the whole budget and
    // starve everything under it to nothing.
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        modifier = modifier.testTag("update_sheet"),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted.copy(alpha = 0.4f)) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdateAlt,
                    contentDescription = null,
                    tint = AnimeRed,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.update_sheet_title),
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.update_sheet_version, available.version),
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.testTag("update_sheet_version"),
            )

            Spacer(Modifier.height(14.dp))

            val downloadingInfo = downloading?.info
            if (downloadingInfo != null) {
                DownloadProgress(
                    progress = downloading.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ReleaseNotes(
                    notes = available.releaseNotes,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (downloading == null) {
                    TextButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)
                                .testTag("update_sheet_later"),
                    ) {
                        Text(stringResource(R.string.update_sheet_later), color = TextSecondary)
                    }
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = onConfirm,
                        modifier =
                            Modifier
                                .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)
                                .testTag("update_sheet_install"),
                    ) {
                        Text(stringResource(R.string.update_sheet_install), color = AnimeRed)
                    }
                } else {
                    // While the APK is downloading the installer takes over; the
                    // sheet only reports progress, so it has no action to offer
                    // and must not offer a dismiss that would abandon a download
                    // whose result the reader cannot see.
                    Text(
                        text = stringResource(R.string.update_sheet_downloading),
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DownloadProgress(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("update_sheet_progress")) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            color = AnimeRed,
            trackColor = CardDark,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.update_sheet_progress, progress),
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
    }
}

/**
 * The changelog, or a line saying there is none.
 *
 * [MarkdownText] needs a `style` for the body text, and a transparent
 * `syntaxHighlightColor`: the library tints fenced code with a highlight colour
 * and there are no code blocks in these notes, so leaving the default would paint
 * an invisible box over any inline span it decides to highlight.
 */
@Composable
private fun ReleaseNotes(
    notes: String,
    modifier: Modifier = Modifier,
) {
    val body =
        notes.ifBlank {
            stringResource(R.string.settings_update_no_notes)
        }
    Column(
        modifier =
            modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        MarkdownText(
            markdown = body,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
            linkColor = AnimeBlue,
            syntaxHighlightColor = Color.Transparent,
            syntaxHighlightTextColor = TextPrimary,
            modifier = Modifier.testTag("update_sheet_notes"),
        )
    }
}

/**
 * The release the state refers to, whether it is waiting to be confirmed or
 * already downloading. Not a composable: it is a plain read of the state, and
 * the confirm callback below needs it from inside a lambda, where a composable
 * call would not compile.
 */
fun updateAvailableInfo(state: UpdateUiState): UpdateInfo? =
    when (state) {
        is UpdateUiState.Available -> state.info
        is UpdateUiState.Downloading -> state.info
        else -> null
    }
