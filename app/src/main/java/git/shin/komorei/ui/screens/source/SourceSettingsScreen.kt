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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import git.shin.komorei.ui.components.search.CompactInput
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import git.shin.komorei.ui.components.AppIcons
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
import git.shin.komorei.ui.tv.tvFocus

@Composable
fun SourceSettingsScreen(
    onBack: () -> Unit,
    onOpenBrowser: (String) -> Unit = {},
    viewModel: SourceSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // A full-screen page (NOT a ModalBottomSheet): a sheet used as a NavHost
    // destination leaves everything above it BLACK — NavHost doesn't draw the
    // previous destination behind it, so the sheet's scrim only dims the app's
    // empty background. All other source sub-pages are full-screen.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar: back + source name ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    // TV focus highlight (no-op on phones).
                    .tvFocus(shape = CircleShape, scale = 1.15f),
            ) {
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
                var migrateConfirm by remember { mutableStateOf(false) }

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
                    //
                    // With NOTHING committed yet, the source's manifest languages are
                    // shown as the effective selection (Aidoku pre-checks them on first
                    // open). The ViewModel keeps that list out of its state so an
                    // explicit reset still reads as "no override" — the default here
                    // is presentation only, and Done is what persists it.
                    uiState.source?.takeIf { it.languages.size > 1 }?.let { source ->
                        val effectiveLanguages =
                            uiState.selectedLanguages.ifEmpty { source.languages }
                        ActionRow(
                            label = stringResource(R.string.source_settings_language),
                            subtitle = effectiveLanguages
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
                        } else if (setting.value is SourceSettingValue.Login
                            || setting.value is SourceSettingValue.Link
                        ) {
                            // Sign-in / website link — open the real browser so
                            // the user can log in; cookies land in the shared
                            // CookieManager that backs WebViewCookieJar, so no
                            // plumbing is needed for media requests.
                            val url = when (val v = setting.value) {
                                is SourceSettingValue.Login -> v.url
                                is SourceSettingValue.Link -> v.url
                                else -> null
                            }
                            url?.let(onOpenBrowser)
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
                        ActionRow(
                            label = stringResource(R.string.source_settings_open_browser),
                            onClick = { onOpenBrowser(source.baseUrl) },
                        )
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

                    // Migrate — re-keys stored library + watch history through the
                    // source's handle_anime_migration / handle_episode_migration.
                    ActionRow(
                        label = stringResource(R.string.source_settings_migrate_data),
                        onClick = { migrateConfirm = true },
                    )

                    Spacer(Modifier.height(16.dp))
                    Spacer(Modifier.height(8.dp))
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
                    val source = uiState.source
                    if (source != null) {
                        LanguageDialog(
                            title = stringResource(R.string.source_settings_language),
                            options = source.languages,
                            // Same presentation-only default as the row above.
                            current = uiState.selectedLanguages.ifEmpty { source.languages },
                            onConfirm = { selection ->
                                viewModel.setLanguages(selection)
                                languageDialog = false
                            },
                            onDismiss = { languageDialog = false },
                        )
                    }
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
                            }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) {
                                Text(
                                    stringResource(R.string.source_settings_reset_confirm_action),
                                    color = AnimeRed,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { resetConfirm = false }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) {
                                Text(
                                    stringResource(R.string.source_settings_cancel),
                                    color = TextSecondary,
                                )
                            }
                        },
                    )
                }

                // Migration confirmation — re-keys stored library + watch history
                // through the source's migration exports.
                if (migrateConfirm) {
                    AlertDialog(
                        onDismissRequest = { migrateConfirm = false },
                        containerColor = CardDark,
                        title = {
                            Text(
                                stringResource(R.string.source_settings_migrate_confirm),
                                color = TextPrimary, fontSize = 16.sp,
                            )
                        },
                        text = {
                            Text(
                                stringResource(
                                    R.string.source_settings_migrate_confirm_message,
                                    uiState.source?.name.orEmpty(),
                                ),
                                color = TextSecondary, fontSize = 14.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.migrateData()
                                migrateConfirm = false
                            }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) {
                                Text(
                                    stringResource(R.string.source_settings_migrate_confirm_action),
                                    color = AnimeRed,
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { migrateConfirm = false }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) {
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
            // TV focus highlight (no-op on phones) — full-width row, ring only.
            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.0f)
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
            // TV focus highlight (no-op on phones) — full-width row, ring only.
            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.0f)
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
                        modifier = Modifier
                            // TV focus highlight (no-op on phones) — dialog option row.
                            .tvFocus(shape = RoundedCornerShape(6.dp), scale = 1.0f)
                            .fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(option) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
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
                        modifier = Modifier
                            // TV focus highlight (no-op on phones) — dialog option row.
                            .tvFocus(shape = RoundedCornerShape(6.dp), scale = 1.0f)
                            .fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(idx) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
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
                        modifier = Modifier
                            // TV focus highlight (no-op on phones) — dialog option row.
                            .tvFocus(shape = RoundedCornerShape(6.dp), scale = 1.0f)
                            .fillMaxWidth().clip(RoundedCornerShape(6.dp))
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
        confirmButton = { TextButton(onClick = { onToggle(selected.value.firstOrNull() ?: ""); onDismiss() }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_done), color = AnimeRed) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
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
                    val toggle = { newChecked: Boolean ->
                        selected.value = if (newChecked) (selected.value + option).toMutableSet() else (selected.value - option).toMutableSet()
                    }
                    Row(
                        modifier = Modifier
                            // TV focus highlight (no-op on phones) — dialog option row.
                            .tvFocus(shape = RoundedCornerShape(6.dp), scale = 1.0f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { toggle(!checked) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.uppercase(), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        Switch(
                            checked = checked,
                            onCheckedChange = toggle,
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
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.value.toList()) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_done), color = AnimeRed) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
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
        confirmButton = { TextButton(onClick = { onConfirm(currentValue) }, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_done), color = AnimeRed) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
    )
}

@Composable
private fun TextDialog(title: String, placeholder: String?, value: String, onValueChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardDark,
        title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
        text = {
            CompactInput(
                value = value,
                onValueChange = onValueChange,
                hint = placeholder,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_done), color = AnimeRed) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) } },
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
    val badgeColor = remember(source.badgeColorHex) { Color(source.badgeColorHex) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(CardDark).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(badgeColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.getSourceIcon(source.id),
                contentDescription = stringResource(R.string.sources_row_icon_cd),
                tint = badgeColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
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
        Button(onClick = onRetry, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), modifier = Modifier.tvFocus(shape = RoundedCornerShape(100), scale = 1.06f)) {
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
