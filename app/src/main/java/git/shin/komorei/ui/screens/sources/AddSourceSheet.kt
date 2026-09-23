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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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

/**
 * The "Thêm nguồn" bottom sheet — a port of Aidoku's `AddSourceView` UX:
 *  - a big **import .aix/.krx** action (SAF file picker), Aidoku's IMPORT_SOURCE;
 *  - a searchable **flat list of every external source merged across all repos**
 *    (Aidoku's `allExternalSources` — duplicate ids are deduped to the newest
 *    advertised version by [buildExternalCatalog]), with one-tap Get buttons;
 *  - **installed sources stripped out** of the list, mirroring
 *    `AddSourceView.filterExternalSources`;
 *  - explicit empty states (no repos / repo down / no matches / all installed);
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

    var query by remember { mutableStateOf("") }
    var showAddRepoDialog by remember { mutableStateOf(false) }

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

    // Search narrows the catalog; installed sources are stripped, so the sheet
    // only ever offers what is actually installable.
    val queryTrimmed = query.trim()
    val queryLower = queryTrimmed.lowercase()
    val visibleSources = remember(catalog.sources, installedIds, queryLower) {
        if (queryLower.isEmpty()) {
            catalog.sources.filter { it.id !in installedIds }
        } else {
            catalog.sources.filter { info ->
                info.id !in installedIds &&
                    (info.name.lowercase().contains(queryLower) || info.id.lowercase().contains(queryLower))
            }
        }
    }
    val allInstalled = catalog.hasRepos && !catalog.loading && catalog.sources.isNotEmpty() &&
        queryTrimmed.isEmpty() && visibleSources.isEmpty()

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
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Import file action (Aidoku's IMPORT_SOURCE button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
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
                if (catalog.hasRepos) {
                    item(key = "section_external") {
                        Text(
                            text = stringResource(R.string.sources_external_section),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
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
                            item(key = if (allInstalled) "all_installed" else "no_results") {
                                if (allInstalled) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.sources_external_all_installed),
                                                color = TextSecondary,
                                                fontSize = 13.sp,
                                            )
                                        }
                                    }
                                } else {
                                    EmptyMessage(text = stringResource(R.string.sources_external_no_results))
                                }
                            }
                        } else {
                            items(visibleSources, key = { it.id }) { info ->
                                ExternalSourceRow(
                                    info = info,
                                    installing = info.id in installingIds,
                                    onGet = { viewModel.installExternal(info) },
                                )
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