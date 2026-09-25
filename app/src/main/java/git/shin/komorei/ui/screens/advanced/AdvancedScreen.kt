package git.shin.komorei.ui.screens.advanced

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.data.LogStore
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus

/**
 * The "Nâng cao" hub — the Komorei counterpart of Aidoku's *Advanced* settings
 * group (`Settings.advancedSettings`).
 *
 * Aidoku groups four things under Advanced: logging (log server / export /
 * display), cache clears, history migration and a full reset. Komorei keeps
 * the same log-server concept: the URL entered here is POSTed to by the local
 * `komorei logcat` server, while the Logs screen remains the local viewer.
 *
 * - **Logging**  → optional remote log stream + local viewer/clear.
 * - **Caches**   → OkHttp's network cache (plus the image/cookie clears that
 *   already live in Settings).
 * - **Reset**    → clear the log buffer, with a confirm dialog, like Aidoku's
 *   destructive Advanced actions.
 */
@Composable
fun AdvancedScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdvancedViewModel = hiltViewModel(),
) {
    var confirmClearLogs by remember { mutableStateOf(false) }
    var logServerInput by rememberSaveable { mutableStateOf(viewModel.logServerUrl.value) }
    val context = LocalContext.current

    // One-shot Toasts for the cache/cookie actions.
    LaunchedEffect(Unit) {
        viewModel.messages.collect { res ->
            Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding()
                .testTag("advanced_screen"),
    ) {
        // ── Top bar ─────────────────────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier
                        .tvFocus(shape = CircleShape, scale = 1.15f)
                        .testTag("advanced_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.advanced_title),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            AdvancedSection(title = stringResource(R.string.logs_title)) {
                LogServerUrlField(
                    value = logServerInput,
                    onValueChange = { logServerInput = it },
                    onSave = { viewModel.saveLogServerUrl(logServerInput) },
                    onClear = {
                        logServerInput = ""
                        viewModel.saveLogServerUrl("")
                    },
                )
                AdvancedRow(
                    icon = Icons.Default.ReceiptLong,
                    title = stringResource(R.string.advanced_open_logs),
                    subtitle = stringResource(R.string.settings_advanced_logs_subtitle),
                    onClick = onOpenLogs,
                    testTag = "advanced_open_logs",
                )
                AdvancedRow(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.logs_clear),
                    subtitle = stringResource(R.string.logs_empty_filtered),
                    onClick = { confirmClearLogs = true },
                    accent = AnimeRed,
                    testTag = "advanced_clear_logs",
                )
            }

            AdvancedSection(title = stringResource(R.string.settings_section_data)) {
                AdvancedRow(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.advanced_clear_source_cache),
                    subtitle = stringResource(R.string.advanced_clear_source_cache_subtitle),
                    onClick = viewModel::clearSourceCache,
                    testTag = "advanced_clear_source_cache",
                )
                AdvancedRow(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.advanced_clear_cookies),
                    subtitle = stringResource(R.string.advanced_clear_cookies_subtitle),
                    onClick = viewModel::clearCookies,
                    testTag = "advanced_clear_cookies",
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (confirmClearLogs) {
        AlertDialog(
            onDismissRequest = { confirmClearLogs = false },
            containerColor = CardDark,
            title = { Text(stringResource(R.string.logs_clear), color = TextPrimary, fontSize = 16.sp) },
            text = {
                Text(
                    text = stringResource(R.string.logs_empty),
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LogStore.clear()
                        confirmClearLogs = false
                    },
                    modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                ) {
                    Text(stringResource(R.string.logs_clear), color = AnimeRed)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClearLogs = false },
                    modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                ) {
                    Text(stringResource(R.string.source_settings_cancel), color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun LogServerUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("advanced_log_server_input"),
        label = { Text(stringResource(R.string.advanced_log_server)) },
        placeholder = { Text(stringResource(R.string.advanced_log_server_placeholder)) },
        supportingText = { Text(stringResource(R.string.advanced_log_server_hint)) },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onSave() }),
        trailingIcon = {
            Row {
                if (value.isNotBlank()) {
                    IconButton(
                        onClick = onClear,
                        modifier =
                            Modifier
                                .tvFocus(shape = CircleShape, scale = 1.05f)
                                .testTag("advanced_log_server_clear"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.advanced_log_server_clear_cd),
                            tint = TextMuted,
                        )
                    }
                }
                IconButton(
                    onClick = onSave,
                    modifier =
                        Modifier
                            .tvFocus(shape = CircleShape, scale = 1.05f)
                            .testTag("advanced_log_server_save"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.advanced_log_server_save_cd),
                        tint = AnimeRed,
                    )
                }
            }
        },
    )
}

@Composable
private fun AdvancedSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = title,
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun AdvancedRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    accent: androidx.compose.ui.graphics.Color = TextSecondary,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                // TV focus highlight (no-op on phones) — full-width row, ring only.
                .tvFocus(shape = RoundedCornerShape(12.dp), scale = 1.0f)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp)
            Text(subtitle, color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}
