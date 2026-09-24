package git.shin.komorei.ui.screens.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.data.compareVersions
import git.shin.komorei.ui.components.ExternalSourceIcon
import git.shin.komorei.ui.components.ShimmerLoadingRow
import git.shin.komorei.ui.components.rememberSystemNavigationBarBottom
import git.shin.komorei.ui.components.search.CompactInput
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.SuccessGreen
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import java.util.Locale

/**
 * The "Thêm nguồn" bottom sheet — a port of Aidoku's `AddSourceView` UX:
 *  - an **import .krx** header icon (SAF file picker), Aidoku's IMPORT_SOURCE
 *    folded into the top bar (no big button);
 *  - a searchable **flat list of every external source merged across all repos**
 *    (Aidoku's `allExternalSources` — duplicate ids are deduped to the newest
 *    advertised version by [buildExternalCatalog]) with one-tap Get buttons;
 *  - **installed sources stay visible** in the list (marked "Đã cài") — unlike
 *    `AddSourceView.filterExternalSources` they are never hidden once on-device;
 *  - installed sources with a **newer advertised version are pulled up into a
 *    separate "Cập nhật" section** with an Update pill (same semantics as the
 *    Sources tab's Updates section);
 *  - a **language filter** button in the header (Aidoku's `AddSourceFilterMenu`)
 *    that narrows the list to sources carrying any selected language tag;
 *  - explicit empty states (no repos / repo down / no matches);
 *  - "Thêm kho" URL dialog and a link to the repo manager screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceSheet(
    onDismiss: () -> Unit,
    onOpenRepos: () -> Unit,
    viewModel: SourcesViewModel,
) {
    val context = LocalContext.current
    val repos by viewModel.repos.collectAsState()
    val repoStates by viewModel.repoStates.collectAsState()
    val installingIds by viewModel.installingIds.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val installedIds = remember(sources) { sources.map { it.source.id }.toSet() }
    // Installed version per id — drives the sheet's "Cập nhật" section.
    val installedVersions = remember(sources) { sources.associate { it.source.id to it.source.version } }

    var query by remember { mutableStateOf("") }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var selectedLanguages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showLanguageFilter by remember { mutableStateOf(false) }

    // Fetch every configured repo when the sheet opens (or repos change) so the
    // catalog already shows sources instead of a stale "Đang tải…".
    LaunchedEffect(repos) {
        viewModel.refreshAllRepos()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val bytes = context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() }
                if (bytes != null) viewModel.importKrx(bytes)
            }
        }
    }

    // Merged, deduped catalog across every loaded repo (Aidoku's allExternalSources).
    val catalog = remember(repos, repoStates) { buildExternalCatalog(repos, repoStates) }

    // Distinct language tags offered by the current catalog for the filter menu
    // (multi-language first, then alphabetical — Aidoku brings local/multi up).
    val availableLanguages = remember(catalog.sources) {
        catalog.sources.flatMap { it.languages }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(compareBy({ it != "multi" }, { it }))
    }

    // Installed sources are NOT stripped from the list (they stay visible,
    // marked "Đã cài"); only the search text and the language filter narrow it.
    val queryTrimmed = query.trim()
    val queryLower = queryTrimmed.lowercase()
    val visibleSources = remember(catalog.sources, selectedLanguages, queryLower) {
        val byLanguage = filterByLanguages(catalog.sources, selectedLanguages)
        if (queryLower.isEmpty()) {
            byLanguage
        } else {
            byLanguage.filter { info ->
                info.name.lowercase().contains(queryLower) || info.id.lowercase().contains(queryLower)
            }
        }
    }
    // Installed rows whose advertised version is newer are pulled up into their
    // own "Cập nhật" section (Update pill) above the regular catalog.
    val (updateSources, otherSources) = remember(visibleSources, installedVersions) {
        partitionUpdates(visibleSources, installedVersions)
    }

    // The sheet grows with its content up to ~92% of the screen height (an
    // Aidoku-style pageSheet); the list column scrolls once it outgrows that.
    val configuration = LocalConfiguration.current
    val sheetMaxHeight = (configuration.screenHeightDp * 0.92f).dp

    // The sheet's Dialog window does not reliably receive system-bar insets, so
    // the list is padded with the HOST window's nav-bar height to let the last
    // row (manage repos) scroll clear of the system navigation bar.
    val navBarBottom = rememberSystemNavigationBarBottom()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .testTag("add_source_sheet"),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sources_import_title),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.sources_import_subtitle),
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                    )
                }
                // Import .krx — a header icon (no big button), Aidoku's
                // IMPORT_SOURCE folded into the top bar.
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.testTag("import_source_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = stringResource(R.string.sources_import_cd),
                        tint = TextSecondary,
                    )
                }
                if (catalog.sources.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { showLanguageFilter = true },
                            modifier = Modifier.testTag("add_source_filter_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = stringResource(R.string.sources_filter_cd),
                                tint = if (selectedLanguages.isEmpty()) TextSecondary else AnimeRed,
                            )
                        }
                        DropdownMenu(
                            expanded = showLanguageFilter,
                            onDismissRequest = { showLanguageFilter = false },
                            containerColor = SurfaceDark,
                        ) {
                            // "Tất cả ngôn ngữ" — clears the filter (show everything).
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.sources_filter_all_languages),
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                    )
                                },
                                leadingIcon = {
                                    if (selectedLanguages.isEmpty()) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = AnimeRed,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    selectedLanguages = emptySet()
                                    showLanguageFilter = false
                                },
                                modifier = Modifier.testTag("add_source_filter_all"),
                            )
                            HorizontalDivider(color = CardBorderDark)
                            availableLanguages.forEach { code ->
                                val selected = code in selectedLanguages
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = languageFilterLabel(code),
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                        )
                                    },
                                    leadingIcon = {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = AnimeRed,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    // Multi-select: keep the menu open (Aidoku uses
                                    // `menuActionDismissDisabled` on the submenu).
                                    onClick = {
                                        selectedLanguages =
                                            if (selected) selectedLanguages - code else selectedLanguages + code
                                    },
                                    modifier = Modifier.testTag("add_source_filter_lang_$code"),
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search — compact pill (same treatment as the Sources tab's search)
            AddSourceSearchBar(
                query = query,
                onQueryChange = { query = it },
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Flat merged external-source list + footer rows (screen plays the
            // role of a full-height pageSheet scroll view).
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = navBarBottom + 24.dp),
            ) {
                if (catalog.hasRepos && catalog.sources.isEmpty()) {
                    // Pre-load states (loading / failed / no sources advertised)
                    // keep the section label pinned; once sources land, the
                    // label is emitted by the rows branch so the "Cập nhật"
                    // section can sit above it.
                    item(key = "section_external") {
                        SheetSectionLabel(stringResource(R.string.sources_external_section))
                    }
                }

                when {
                    // No repos configured at all — prompt to add one.
                    !catalog.hasRepos -> {
                        item(key = "no_repos") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.sources_repos_empty),
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100))
                                        .background(AnimeRed)
                                        .clickable { showAddRepoDialog = true }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .testTag("no_repos_add_button"),
                                ) {
                                    Text(
                                        text = stringResource(R.string.sources_repos_add),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                        }
                    }

                    // Repos are resolving and nothing has landed yet — skeleton.
                    catalog.sources.isEmpty() && catalog.loading -> {
                        item(key = "loading") {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                repeat(4) {
                                    ShimmerLoadingRow()
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }

                    // Every repo failed to load — retry all.
                    catalog.sources.isEmpty() && catalog.failed -> {
                        item(key = "failed_all") {
                            FailedNotice(onRetry = { viewModel.refreshAllRepos() })
                        }
                    }

                    // Repos loaded but advertised no sources.
                    catalog.sources.isEmpty() -> {
                        item(key = "no_sources") {
                            EmptyMessage(text = stringResource(R.string.sources_external_no_results))
                        }
                    }

                    else -> {
                        if (visibleSources.isEmpty()) {
                            item(key = "no_results") {
                                EmptyMessage(text = stringResource(R.string.sources_external_no_results))
                            }
                        } else {
                            // Sources with a newer advertised version — pulled
                            // up into their own section with an Update pill.
                            if (updateSources.isNotEmpty()) {
                                item(key = "section_updates") {
                                    SheetSectionLabel(stringResource(R.string.sources_section_updates))
                                }
                                items(updateSources, key = { it.id }) { info ->
                                    ExternalSourceRow(
                                        info = info,
                                        installing = info.id in installingIds,
                                        installed = true,
                                        hasUpdate = true,
                                        onGet = { viewModel.installExternal(info) },
                                        onUpdate = { viewModel.installExternal(info) },
                                    )
                                }
                            }
                            // Everything else — the regular catalog rows.
                            if (otherSources.isNotEmpty()) {
                                item(key = "section_external") {
                                    SheetSectionLabel(stringResource(R.string.sources_external_section))
                                }
                                items(otherSources, key = { it.id }) { info ->
                                    ExternalSourceRow(
                                        info = info,
                                        installing = info.id in installingIds,
                                        installed = info.id in installedIds,
                                        hasUpdate = false,
                                        onGet = { viewModel.installExternal(info) },
                                        onUpdate = {},
                                    )
                                }
                            }
                        }
                        // Remaining repos still resolving → inline skeleton.
                        if (catalog.loading) {
                            item(key = "loading_more") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    ShimmerLoadingRow()
                                }
                            }
                        }
                        // Some repos went down — notice + retry.
                        if (catalog.failed) {
                            item(key = "failed_notice") {
                                FailedNotice(onRetry = { viewModel.refreshAllRepos() })
                            }
                        }
                    }
                }

                // Footer — repo management stays reachable but de-emphasized
                // (Aidoku keeps source lists out of the add-source flow).
                item(key = "add_repo") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddRepoDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("add_repo_button"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = AnimeRed,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sources_repos_add),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                item(key = "manage_repos") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRepos() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("manage_repos_button"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sources_repos_manage),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }

    if (showAddRepoDialog) {
        AddRepoDialog(
            onDismiss = { showAddRepoDialog = false },
            onConfirm = { url ->
                if (viewModel.addRepo(url)) {
                    showAddRepoDialog = false
                }
            },
        )
    }
}

/** Compact 40dp search pill mirroring the Sources tab's own search field. */
@Composable
private fun AddSourceSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardDark)
            .border(1.dp, CardBorderDark, RoundedCornerShape(20.dp))
            .testTag("add_source_search_input"),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = stringResource(R.string.sources_search_cd),
            tint = if (query.isNotBlank()) AnimeRed else TextMuted,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(18.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(AnimeRed),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 38.dp, end = 38.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sources_external_search_hint),
                            color = TextMuted,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("add_source_search_input_field"),
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(32.dp)
                    .testTag("add_source_search_clear"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.search_clear_cd),
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Display name of a language tag in the filter menu ("Đa ngôn ngữ" for multi). */
@Composable
private fun languageFilterLabel(code: String): String {
    if (code == "multi" || code.isBlank()) return stringResource(R.string.sources_language_multi)
    return remember(code) { Locale.forLanguageTag(code).displayLanguage }.ifBlank { code }
}

/** Section header used inside the sheet's list ("Cập nhật", "Nguồn ngoài"). */
@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Muted inline message row used for search/no-results empty states. */
@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** "Một số kho không tải được" + Thử lại pill (row-level or full-state variant). */
@Composable
private fun FailedNotice(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.sources_external_repos_partial_failed),
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.sources_repos_retry),
            color = AnimeRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .clickable(onClick = onRetry)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag("external_retry_button"),
        )
    }
}

@Composable
private fun ExternalSourceRow(
    info: ExternalSourceInfo,
    installing: Boolean,
    installed: Boolean,
    hasUpdate: Boolean,
    onGet: () -> Unit,
    onUpdate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardDark),
            contentAlignment = Alignment.Center,
        ) {
            ExternalSourceIcon(
                sourceId = info.id,
                iconUrl = info.iconUrl,
                contentDescription = stringResource(R.string.sources_row_icon_cd),
                fallbackTint = TextSecondary,
                // Full-bleed artwork, like the Sources tab — the icon covers the
                // whole 40dp box (object-fit: cover); the vector fallback stays
                // a small centered glyph on the CardDark backdrop.
                iconSize = 40.dp,
                fallbackIconSize = 20.dp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = info.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (info.contentRating >= 2) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AnimeRed)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sources_badge_nsfw),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 12.sp,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = sourceVersionSubtitle(info.version, info.languages),
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (info.repoName != null) {
                Text(
                    text = stringResource(R.string.sources_external_repo_label, info.repoName!!),
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            installing -> {
                Text(
                    text = stringResource(R.string.sources_external_getting),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            hasUpdate -> {
                // Newer advertised version — "Cập nhật" pill (same as the
                // Sources tab's Updates section).
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(AnimeRed)
                        .clickable(onClick = onUpdate)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("source_row_update_${info.id}"),
                ) {
                    Text(
                        text = stringResource(R.string.sources_action_update),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp,
                    )
                }
            }
            installed -> {
                // Already on-device row — kept visible, marked instead of hidden.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(CardDark)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("source_row_installed_${info.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.sources_external_installed),
                        color = SuccessGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp,
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(AnimeRed)
                        .clickable(onClick = onGet)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sources_external_get),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun AddRepoDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sources_repos_add)) },
        text = {
            Column {
                CompactInput(
                    value = url,
                    onValueChange = { url = it },
                    hint = stringResource(R.string.sources_repos_add_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_repo_url_input"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sources_repos_add_hint),
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim()) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.sources_repos_add), color = AnimeRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sources_cancel))
            }
        },
    )
}