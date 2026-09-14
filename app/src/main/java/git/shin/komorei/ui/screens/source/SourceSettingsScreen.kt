package git.shin.komorei.ui.screens.source

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Source
import git.shin.komorei.model.SourceSetting
import git.shin.komorei.model.SourceSettingValue
import git.shin.komorei.ui.components.shimmerEffect
import git.shin.komorei.ui.theme.AnimeBlue
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.SurfaceVariantDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

@Composable
fun SourceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SourceSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding(),
    ) {
        // ── Top bar: back + source name ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = uiState.source?.name ?: stringResource(R.string.source_settings_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Content ─────────────────────────────────────────────────────
        when {
            uiState.isLoading && uiState.settings == null -> SettingsSkeleton()
            uiState.error && uiState.settings == null ->
                ErrorSettings(onRetry = { viewModel.loadSettings() })
            uiState.settings.isNullOrEmpty() -> EmptySettings()
            else -> {
                val settings = uiState.settings!!
                var dialogSetting by remember { mutableStateOf<SourceSetting?>(null) }
                var dialogText by remember { mutableStateOf("") }
                var languageDialog by remember { mutableStateOf(false) }
                var resetConfirm by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Source info card
                    uiState.source?.let { SourceInfoCard(it) }

                    // App-injected language picker (Aidoku: legacy `SourceInfoViewController`
                    // shows a "Language" section for any multi-language source; the
                    // selection is written to `{sourceId}.languages` so the source can
                    // read it back via defaults_get("languages")).
                    uiState.source?.takeIf { it.languages.size > 1 }?.let { source ->
                        ActionRow(
                            label = stringResource(R.string.source_settings_language),
                            subtitle = uiState.selectedLanguages
                                .joinToString(", ") { it.uppercase() }
                                .ifEmpty { null },
                            onClick = { languageDialog = true },
                        )
                    }

                    // Setting rows
                    val onSettingClick: (SourceSetting) -> Unit = { setting ->
                        if (setting.value is SourceSettingValue.Button) {
                            // One-shot action button — not a dialog: send its
                            // `notification` straight to the source.
                            viewModel.runSetting(setting)
                        } else {
                            dialogSetting = setting
                            dialogText = extractTextDefault(setting)
                        }
                    }
                    settings.forEach { setting ->
                        if (setting.value is SourceSettingValue.Group) {
                            val group = setting.value as SourceSettingValue.Group
                            Text(
                                text = setting.title,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                            group.items.forEach { child ->
                                SettingRow(
                                    setting = child,
                                    onToggle = { viewModel.toggleSetting(child.key, child.value.toggleDefault()) },
                                    onClick = onSettingClick,
                                )
                            }
                        } else {
                            SettingRow(
                                setting = setting,
                                onToggle = { viewModel.toggleSetting(setting.key, setting.value.toggleDefault()) },
                                onClick = onSettingClick,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    uiState.source?.let { source ->
                        if (source.baseUrl.isNotBlank()) {
                            ActionRow(
                                label = stringResource(R.string.source_home_open_website),
                                onClick = { openSourceWebsite(context, source.baseUrl) },
                            )
                        }
                    }

                    ActionRow(
                        label = stringResource(R.string.source_settings_clear_cache),
                        onClick = { viewModel.clearCachedHome() },
                    )

                    // Clear cookies for this source's domain (Aidoku: "Clear
                    // Source Cache" removes the source URLs' cookies too).
                    ActionRow(
                        label = stringResource(R.string.source_settings_clear_cookies),
                        onClick = { viewModel.clearCookies() },
                    )

                    // Reset Settings — wipes every `{sourceId}.` default (Aidoku:
                    // `removeSettings(from:)` deletes all UserDefaults keys with the
                    // source prefix).
                    ActionRow(
                        label = stringResource(R.string.source_settings_reset_settings),
                        onClick = { resetConfirm = true },
                    )

                    Spacer(Modifier.height(16.dp))
                }

                // ── Dialogs ──────────────────────────────────────────────
                dialogSetting?.let { setting ->
                    when (val v = setting.value) {
                        is SourceSettingValue.Toggle -> dialogSetting = null
                        is SourceSettingValue.Select -> SelectDialog(
                            title = setting.title, options = v.values, titles = v.titles,
                            current = v.default,
                            onSelect = { viewModel.selectSetting(setting.key, it); dialogSetting = null },
                            onDismiss = { dialogSetting = null },
                        )
                        is SourceSettingValue.Picker -> SelectDialog(
                            title = setting.title, options = v.values, titles = v.titles,
                            current = v.default,
                            onSelect = { viewModel.selectSetting(setting.key, it); dialogSetting = null },
                            onDismiss = { dialogSetting = null },
                        )
                        is SourceSettingValue.Segment -> SegmentDialog(
                            title = setting.title, options = v.options, currentIndex = v.default,
                            onSelect = { viewModel.selectSegment(setting.key, it); dialogSetting = null },
                            onDismiss = { dialogSetting = null },
                        )
                        is SourceSettingValue.MultiSelect -> MultiSelectDialog(
                            title = setting.title, options = v.values, titles = v.titles,
                            current = v.default.orEmpty(),
                            onToggle = { viewModel.toggleMultiSelect(setting.key, it, v.default.orEmpty()) },
                            onDismiss = { dialogSetting = null },
                        )
                        is SourceSettingValue.Stepper -> StepperDialog(
                            title = setting.title,
                            value = v.default ?: v.minimumValue,
                            min = v.minimumValue, max = v.maximumValue, step = v.stepValue,
                            onConfirm = { viewModel.setStepper(setting.key, it); dialogSetting = null },
                            onDismiss = { dialogSetting = null },
                        )
                        is SourceSettingValue.Text -> TextDialog(
                            title = setting.title, placeholder = v.placeholder,
                            value = dialogText, onValueChange = { dialogText = it },
                            onConfirm = { viewModel.setText(setting.key, dialogText); dialogSetting = null },
                            onDismiss = { dialogSetting = null },
                        )
                        else -> dialogSetting = null
                    }
                }

                // App-injected language picker.
                if (languageDialog) {
                    val source = uiState.source ?: return@Column
                    LanguageDialog(
                        title = stringResource(R.string.source_settings_language),
                        options = source.languages,
                        current = uiState.selectedLanguages,
                        onConfirm = { selection ->
                            viewModel.setLanguages(selection)
                            languageDialog = false
                        },
                        onDismiss = { languageDialog = false },
                    )
                }

                // Reset Settings confirmation.
                if (resetConfirm) {
                    AlertDialog(
                        onDismissRequest = { resetConfirm = false },
                        containerColor = CardDark,
                        title = {
                            Text(
                                stringResource(R.string.source_settings_reset_settings),
                                color = TextPrimary, fontSize = 16.sp,
                            )
                        },
                        text = {
                            Text(
                                stringResource(
                                    R.string.source_settings_reset_confirm,
                                    uiState.source?.name.orEmpty(),
                                ),
                                color = TextSecondary, fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.resetSettings()
                                resetConfirm = false
                            }) {
                                Text(
                                    stringResource(R.string.source_settings_reset_confirm_action),
                                    color = AnimeRed,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { resetConfirm = false }) {
                                Text(
                                    stringResource(R.string.source_settings_cancel),
                                    color = TextSecondary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Setting rows — compact, consistent with SourceHomeScreen
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingRow(
    setting: SourceSetting,
    onToggle: (Boolean) -> Unit,
    onClick: (SourceSetting) -> Unit,
) {
    when (val v = setting.value) {
        is SourceSettingValue.Toggle -> ToggleRow(setting.title, v.subtitle, v.default, onToggle)
        is SourceSettingValue.Button -> ActionRow(setting.title, null, isAccent = true) { onClick(setting) }
        is SourceSettingValue.Link -> ActionRow(setting.title, v.url) { onClick(setting) }
        is SourceSettingValue.Login -> ActionRow(setting.title, v.url) { onClick(setting) }
        else -> ClickableRow(setting.title, v.rowSubtitle()) { onClick(setting) }
    }
}

private fun SourceSettingValue.rowSubtitle(): String? = when (this) {
    is SourceSettingValue.Group -> null
    is SourceSettingValue.Page -> null
    is SourceSettingValue.Toggle -> null
    is SourceSettingValue.Select -> default
    is SourceSettingValue.MultiSelect -> default?.takeIf { it.isNotEmpty() }?.joinToString(", ")
    is SourceSettingValue.Stepper -> default?.let { String.format("%.1f", it) }
    is SourceSettingValue.Segment -> default?.let { options.getOrNull(it) }
    is SourceSettingValue.Text -> default?.ifEmpty { null }
    is SourceSettingValue.EditableList -> default?.joinToString(", ")
    is SourceSettingValue.Picker -> default
    is SourceSettingValue.Link -> url
    is SourceSettingValue.Login -> null
    is SourceSettingValue.Button -> null
}

private fun SourceSettingValue.toggleDefault(): Boolean = when (this) {
    is SourceSettingValue.Toggle -> default
    else -> false
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardDark)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp)
            subtitle?.let {
                Text(it, color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextPrimary,
                checkedTrackColor = AnimeBlue,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceVariantDark,
                uncheckedBorderColor = CardBorderDark,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    subtitle: String? = null,
    isAccent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isAccent) AnimeRed else CardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            Text(label, color = if (isAccent) TextPrimary else AnimeRed, fontSize = 14.sp, lineHeight = 18.sp)
            subtitle?.let {
                Text(it, color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ClickableRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp)
            subtitle?.let {
                Text(it, color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", color = TextSecondary, fontSize = 16.sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Dialogs
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SelectDialog(title: String, options: List<String>, titles: List<String>?, current: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardDark,
        title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                options.forEachIndexed { idx, option ->
                    val display = titles?.getOrNull(idx) ?: option
                    val sel = option == current
                    Text(
                        text = "${if (sel) "● " else "  "}$display",
                        color = if (sel) AnimeRed else TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(option) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
    )
}

@Composable
private fun SegmentDialog(title: String, options: List<String>, currentIndex: Int?, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardDark,
        title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                options.forEachIndexed { idx, option ->
                    val sel = idx == currentIndex
                    Text(
                        text = "${if (sel) "● " else "  "}$option",
                        color = if (sel) AnimeRed else TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(idx) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
    )
}

@Composable
private fun MultiSelectDialog(title: String, options: List<String>, titles: List<String>?, current: List<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val selected = remember { mutableStateOf(current.toMutableSet()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardDark,
        title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                options.forEachIndexed { idx, option ->
                    val display = titles?.getOrNull(idx) ?: option
                    val checked = option in selected.value
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable { if (checked) selected.value.remove(option) else selected.value.add(option) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (checked) "☑" else "☐", color = if (checked) AnimeRed else TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(display, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onToggle(selected.value.firstOrNull() ?: ""); onDismiss() }) { Text(stringResource(R.string.source_settings_done), color = AnimeRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
    )
}

/**
 * App-injected language picker (Aidoku's legacy `SourceInfoViewController`
 * section 0): multi-select over the source's manifest languages, committing
 * the FULL selection on Done to `{sourceId}.languages`.
 */
@Composable
private fun LanguageDialog(title: String, options: List<String>, current: List<String>, onConfirm: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val selected = remember { mutableStateOf(current.toMutableSet()) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardDark,
        title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column {
                options.forEach { option ->
                    val checked = option in selected.value
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable { if (checked) selected.value.remove(option) else selected.value.add(option) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (checked) "☑" else "☐", color = if (checked) AnimeRed else TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(option.uppercase(), color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.value.toList()) }) { Text(stringResource(R.string.source_settings_done), color = AnimeRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
    )
}

@Composable
private fun StepperDialog(title: String, value: Double, min: Double, max: Double, step: Double?, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var currentValue by remember { mutableDoubleStateOf(value) }
    val steps = if (step != null) ((max - min) / step).toInt() - 1 else 20
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardDark,
        title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format("%.1f", currentValue), color = AnimeRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Slider(
                    value = currentValue.toFloat(), onValueChange = { currentValue = it.toDouble() },
                    valueRange = min.toFloat()..max.toFloat(), steps = steps,
                    colors = SliderDefaults.colors(thumbColor = AnimeRed, activeTrackColor = AnimeRed),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(String.format("%.0f", min), color = TextMuted, fontSize = 11.sp)
                    Text(String.format("%.0f", max), color = TextMuted, fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(currentValue) }) { Text(stringResource(R.string.source_settings_done), color = AnimeRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
    )
}

@Composable
private fun TextDialog(title: String, placeholder: String?, value: String, onValueChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardDark,
        title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                placeholder = placeholder?.let { { Text(it, color = TextSecondary) } },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.source_settings_done), color = AnimeRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════════════════════════════════════════

private fun extractTextDefault(setting: SourceSetting): String = when (val v = setting.value) {
    is SourceSettingValue.Text -> v.default.orEmpty()
    is SourceSettingValue.EditableList -> v.default?.firstOrNull().orEmpty()
    else -> ""
}

@Composable
private fun SourceInfoCard(source: Source) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(CardDark).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (source.languages.isNotEmpty()) {
                Text(source.languages.joinToString(", ").uppercase(), color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
        if (source.contentRating != 0) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SurfaceVariantDark)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text("18+", color = AnimeRed, fontSize = 10.sp)
            }
        }
    }
}

// ── Loading / Error / Empty ────────────────────────────────────────────────

@Composable
private fun SettingsSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(CardDark).shimmerEffect())
        repeat(5) {
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(CardDark).shimmerEffect())
        }
    }
}

@Composable
private fun ErrorSettings(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.source_settings_error), color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Button(onClick = onRetry, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            Text(stringResource(R.string.source_settings_reload), fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptySettings() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.source_settings_empty), color = TextMuted, fontSize = 13.sp)
    }
}
