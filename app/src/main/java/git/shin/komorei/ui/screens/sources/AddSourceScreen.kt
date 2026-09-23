package git.shin.komorei.ui.screens.sources

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.ui.components.ExternalSourceIcon
import git.shin.komorei.ui.components.ShimmerLoadingRow
import git.shin.komorei.ui.components.search.CompactInput
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

/**
 * Full-screen "Thêm nguồn" page — a faithful port of Aidoku's `AddSourceView`:
 *  - top bar: back (the CloseButton cancellation action) + "Thêm nguồn" title;
 *  - the import `.aix`/`.krx` button (Aidoku's LargeButton IMPORT_SOURCE);
 *  - ONE always-visible **"Nguồn ngoài"** section whose header carries the
 *    magnifying-glass search toggle, exactly like AddSourceView's
 *    EXTERNAL_SOURCES header (search bar appears under the top bar with a
 *    "Hủy" cancel, mirroring `customSearchable`);
 *  - the flat, deduped external-source catalog (installed sources stripped,
 *    see [buildExternalCatalog]) rendered as icon/name/version rows with
 *    one-tap Get;
 *  - **centered infoView empty states** (title + subtitle) matching Aidoku's
 *    `infoView(title:subtitle:)`: no repos yet / no available sources / all
 *    installed / no search results, plus a retry state for repos that failed.
 *
 * Repo management deliberately stays OUT of this flow (Aidoku keeps source
 * lists in Settings) — only the no-repos state offers an inline "Thêm kho"
 * CTA; the full manager lives behind Settings → `SourceReposScreen`.
 */
@Composable
fun AddSourceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val repos by viewModel.repos.collectAsState()
    val repoStates by viewModel.repoStates.collectAsState()
    val installingIds by viewModel.installingIds.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val installedIds = remember(sources) { sources.map { it.source.id }.toSet() }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    // The Sources tab is no longer composed while this page is pushed, so this
    // destination owns its own Toast collector (same as SourceReposScreen).
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    // Fetch every configured repo when the page opens (or repos change) so the
    // catalog is already populated (never a stale "Đang tải…").
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

    // Aidoku splits the states in two: `externalSources` (installed stripped,
    // NO_EXTERNAL_SOURCES / ALL_SOURCES_INSTALLED / NO_AVAILABLE_SOURCES) is
    // resolved BEFORE the search text, and NO_RESULTS only applies on top of a
    // non-empty stripped list.
    val installable = remember(catalog.sources, installedIds) {
        catalog.sources.filter { it.id !in installedIds }
    }
    val allInstalled = catalog.hasRepos && !catalog.loading && catalog.sources.isNotEmpty() &&
        installable.isEmpty()
    val queryLower = query.trim().lowercase()
    val filtered = remember(installable, queryLower) {
        if (queryLower.isEmpty()) {
            installable
        } else {
            installable.filter { info ->
                info.name.lowercase().contains(queryLower) || info.id.lowercase().contains(queryLower)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .testTag("add_source_screen"),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("add_source_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.sources_import_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        // Search (shown while toggled from the section header) — the pill +
        // "Hủy" cancel mirrors Aidoku's customSearchable onCancel.
        if (searching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AddSourceSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sources_cancel),
                    color = AnimeRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            searching = false
                            query = ""
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("add_source_search_cancel"),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Import file action (Aidoku's IMPORT_SOURCE LargeButton).
            item(key = "import") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .clickable { importLauncher.launch(arrayOf("*/*")) }
                        .padding(16.dp)
                        .testTag("import_source_button"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddCircle,
                        contentDescription = stringResource(R.string.sources_import_cd),
                        tint = AnimeRed,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.sources_import_button),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // EXTERNAL_SOURCES section header — always rendered (its content is
            // the infoView empty states too), with the search toggle appended
            // only while there are sources and search is off.
            item(key = "section_external") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.sources_external_section),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (catalog.sources.isNotEmpty() && !searching) {
                        IconButton(
                            onClick = { searching = true },
                            modifier = Modifier.testTag("external_search_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.sources_search_cd),
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            when {
                // No repos configured at all → NO_EXTERNAL_SOURCES infoView
                // (+ inline "Thêm kho" CTA; Aidoku points at Settings instead).
                !catalog.hasRepos -> {
                    item(key = "no_repos") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            InfoView(
                                title = stringResource(R.string.sources_external_info_no_repos_title),
                                subtitle = stringResource(R.string.sources_external_info_no_repos_sub),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(AnimeRed)
                                    .clickable { showAddRepoDialog = true }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("add_repo_button"),
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
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            repeat(4) {
                                ShimmerLoadingRow()
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }

                // Every repo failed to load → centered info + retry.
                catalog.sources.isEmpty() && catalog.failed -> {
                    item(key = "failed_all") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            InfoView(
                                title = stringResource(R.string.sources_external_info_failed_title),
                                subtitle = stringResource(R.string.sources_external_info_failed_sub),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .clickable { viewModel.refreshAllRepos() }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("external_retry_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.sources_repos_retry),
                                    color = AnimeRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                }

                // Repos loaded but advertised no sources → NO_AVAILABLE_SOURCES.
                catalog.sources.isEmpty() -> {
                    item(key = "no_available") {
                        InfoView(
                            title = stringResource(R.string.sources_external_info_no_available_title),
                            subtitle = stringResource(R.string.sources_external_info_no_available_sub),
                        )
                    }
                }

                else -> {
                    when {
                        // Every installable source is already on the device →
                        // ALL_SOURCES_INSTALLED (shown even mid-search, like Aidoku).
                        allInstalled -> {
                            item(key = "all_installed") {
                                InfoView(
                                    title = stringResource(R.string.sources_external_info_all_installed_title),
                                    subtitle = stringResource(R.string.sources_external_all_installed),
                                )
                            }
                        }
                        // Non-empty list but the search text matched nothing.
                        filtered.isEmpty() -> {
                            item(key = "no_results") {
                                Text(
                                    text = stringResource(R.string.sources_external_no_results),
                                    color = TextMuted,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 20.dp),
                                )
                            }
                        }
                        else -> {
                            items(filtered, key = { it.id }) { info ->
                                ExternalSourceRow(
                                    info = info,
                                    installing = info.id in installingIds,
                                    onGet = { viewModel.installExternal(info) },
                                )
                            }
                            // Remaining repos still resolving → inline skeleton.
                            if (catalog.loading) {
                                item(key = "loading_more") {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        ShimmerLoadingRow()
                                    }
                                }
                            }
                            // Some repos went down → notice + retry.
                            if (catalog.failed) {
                                item(key = "failed_notice") {
                                    FailedNotice(onRetry = { viewModel.refreshAllRepos() })
                                }
                            }
                        }
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

/** Aidoku's `infoView(title:subtitle:)` — centered title + secondary subtitle. */
@Composable
private fun InfoView(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Compact 40dp search pill mirroring the Sources tab's own search field. */
@Composable
private fun AddSourceSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
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

/** "Một số kho không tải được" + Thử lại pill under the rows. */
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
    onGet: () -> Unit,
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
                iconSize = 20.dp,
                fallbackIconSize = 20.dp,
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
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (installing) {
            Text(
                text = stringResource(R.string.sources_external_getting),
                color = TextMuted,
                fontSize = 12.sp,
            )
        } else {
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