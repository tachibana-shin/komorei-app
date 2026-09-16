package git.shin.komorei.ui.screens.sources

import git.shin.komorei.ui.components.search.CompactInput
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.R
import git.shin.komorei.data.ExternalSourceInfo
import git.shin.komorei.ui.components.AppIcons
import git.shin.komorei.ui.components.ShimmerLoadingRow
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.theme.SuccessGreen

/**
 * The "Thêm nguồn" bottom sheet — Aidoku's Add Source page:
 *  - a big **import .aix/.krx** action (SAF file picker);
 *  - per-repo expansion showing the repo's external sources with Get buttons;
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

    var expandedRepo by remember { mutableStateOf<String?>(null) }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    // Fetch every configured repo when the sheet opens (or repos change) so the
    // collapsed rows already show Loaded/Unavailable instead of a stale "Đang tải…".
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState())
            .testTag("add_source_sheet")) {

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

            // Import file action
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

            Spacer(modifier = Modifier.height(20.dp))

            // Repos section
            Text(
                text = stringResource(R.string.sources_repos_section),
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (repos.isEmpty()) {
                Text(
                    text = stringResource(R.string.sources_repos_empty),
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                repos.forEach { url ->
                    val state = repoStates[url]
                    val expanded = expandedRepo == url
                    RepoRow(
                        url = url,
                        state = state,
                        expanded = expanded,
                        onClick = {
                            if (expanded) {
                                expandedRepo = null
                            } else {
                                expandedRepo = url
                                if (state == null || state is RepoSectionState.Unavailable) {
                                    viewModel.loadRepo(url)
                                }
                            }
                        },
                        onRetry = { viewModel.loadRepo(url) },
                    )
                    if (expanded) {
                        when (val st = state) {
                            null, is RepoSectionState.Loading -> ShimmerLoadingRow()
                            is RepoSectionState.Loaded -> {
                                if (st.sources.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.sources_repos_list_empty),
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 40.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                                    )
                                } else {
                                    st.sources.forEach { info ->
                                        ExternalSourceRow(
                                            info = info,
                                            installing = info.id in installingIds,
                                            installed = info.id in installedIds,
                                            onGet = { viewModel.installExternal(info) },
                                        )
                                    }
                                }
                            }
                            RepoSectionState.Unavailable -> {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 40.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
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
                                        text = stringResource(R.string.sources_repos_unavailable),
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
                                            .clickable { viewModel.loadRepo(url) }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Add repo row
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

            // Manage repos row
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

@Composable
private fun RepoRow(
    url: String,
    state: RepoSectionState?,
    expanded: Boolean,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("repo_row_$url"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = if (state is RepoSectionState.Unavailable) TextMuted else AnimeRed,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (state as? RepoSectionState.Loaded)?.name ?: url.removePrefix("https://").removePrefix("http://"),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = url,
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (state) {
            is RepoSectionState.Loaded -> {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.sources_repos_hide_sources)
                    } else {
                        stringResource(R.string.sources_repos_fetch_sources)
                    },
                    color = AnimeRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            is RepoSectionState.Unavailable -> {
                Text(
                    text = stringResource(R.string.sources_repos_unavailable),
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
            null, is RepoSectionState.Loading -> {
                Text(
                    text = stringResource(R.string.sources_repos_loading),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ExternalSourceRow(
    info: ExternalSourceInfo,
    installing: Boolean,
    installed: Boolean,
    onGet: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardDark),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.getSourceIcon(info.id),
                contentDescription = stringResource(R.string.sources_row_icon_cd),
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        when {
            installing -> {
                Text(
                    text = stringResource(R.string.sources_external_getting),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            installed -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(SuccessGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sources_external_installed),
                        color = SuccessGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
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