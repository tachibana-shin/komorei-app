package git.shin.komorei.ui.screens.sources

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
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
import git.shin.komorei.model.Source
import git.shin.komorei.ui.components.AppIcons
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

/**
 * The "Nguồn" tab — a port of Aidoku's Browse tab:
 *  - searchable source list with **Updates / Pinned / Installed** sections;
 *  - long-press (or ⋮) context menu: enable/disable, pin/unpin, uninstall;
 *  - "Update" pill rows in the Updates section;
 *  - Add-source sheet (import .aix/.krx + browse repos) via the "+" button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourcesScreen(
    onOpenRepos: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val sources by viewModel.sources.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var uninstallCandidate by remember { mutableStateOf<SourceUiState?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }

    val updates = sources.filter { it.updateAvailableVersion != null }
    val pinned = sources.filter { it.pinnedIndex >= 0 && it.updateAvailableVersion == null }
    val installed = sources.filter { it.pinnedIndex < 0 && it.updateAvailableVersion == null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .testTag("sources_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sources_title),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.sources_header_count_format,
                        sources.size,
                        sources.count { it.enabled },
                    ),
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = { viewModel.checkForUpdates() },
                modifier = Modifier.testTag("sources_refresh_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.sources_refresh_cd),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { showAddSheet = true },
                modifier = Modifier.testTag("add_source_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.sources_add_cd),
                    tint = AnimeRed,
                )
            }
        }

        // Search — compact pill (BasicTextField in a 40dp rounded box, not the
        // 56dp M3 OutlinedTextField which looked oversized)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardDark)
                .border(1.dp, CardBorderDark, RoundedCornerShape(20.dp))
                .testTag("sources_search_input"),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.sources_search_cd),
                tint = if (searchQuery.isNotBlank()) AnimeRed else TextMuted,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(18.dp),
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
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
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.sources_search_hint),
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
                    .testTag("sources_search_input_field"),
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.setSearchQuery("") },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(32.dp)
                        .testTag("sources_search_clear"),
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

        Spacer(modifier = Modifier.height(4.dp))

        // List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (sources.isEmpty()) {
                item(key = "empty") {
                    EmptyState(onAdd = { showAddSheet = true })
                }
            } else {
                if (updates.isNotEmpty()) {
                    item(key = "header_updates") {
                        SectionLabel(stringResource(R.string.sources_section_updates))
                    }
                    items(updates, key = { it.source.id }) { item ->
                        SourceRow(
                            item = item,
                            onUpdate = { viewModel.updateSource(item.source) },
                            onToggleEnabled = { viewModel.setEnabled(item.source, !item.enabled) },
                            onTogglePinned = { viewModel.togglePinned(item.source) },
                            onRequestUninstall = { uninstallCandidate = item },
                        )
                    }
                }
                if (pinned.isNotEmpty()) {
                    item(key = "header_pinned") {
                        SectionLabel(stringResource(R.string.sources_section_pinned))
                    }
                    items(pinned, key = { it.source.id }) { item ->
                        SourceRow(
                            item = item,
                            onUpdate = { viewModel.updateSource(item.source) },
                            onToggleEnabled = { viewModel.setEnabled(item.source, !item.enabled) },
                            onTogglePinned = { viewModel.togglePinned(item.source) },
                            onRequestUninstall = { uninstallCandidate = item },
                        )
                    }
                }
                item(key = "header_installed") {
                    SectionLabel(stringResource(R.string.sources_section_installed))
                }
                items(installed, key = { it.source.id }) { item ->
                    SourceRow(
                        item = item,
                        onUpdate = { viewModel.updateSource(item.source) },
                        onToggleEnabled = { viewModel.setEnabled(item.source, !item.enabled) },
                        onTogglePinned = { viewModel.togglePinned(item.source) },
                        onRequestUninstall = { uninstallCandidate = item },
                    )
                }
            }
        }
    }

    uninstallCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { uninstallCandidate = null },
            title = { Text(stringResource(R.string.sources_confirm_uninstall_title)) },
            text = {
                Text(
                    stringResource(R.string.sources_confirm_uninstall_message, candidate.source.name),
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.uninstall(candidate.source)
                        uninstallCandidate = null
                    },
                ) {
                    Text(stringResource(R.string.sources_uninstall), color = AnimeRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallCandidate = null }) {
                    Text(stringResource(R.string.sources_cancel))
                }
            },
        )
    }

    if (showAddSheet) {
        AddSourceSheet(
            onDismiss = { showAddSheet = false },
            onOpenRepos = {
                showAddSheet = false
                onOpenRepos()
            },
            viewModel = viewModel,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(
    item: SourceUiState,
    onUpdate: () -> Unit,
    onToggleEnabled: () -> Unit,
    onTogglePinned: () -> Unit,
    onRequestUninstall: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val subtitle = sourceVersionSubtitle(item.source.version, item.source.languages)
    val badgeColor = remember(item.source.badgeColorHex) { Color(item.source.badgeColorHex) }
    val enabledLabel = if (item.enabled) {
        stringResource(R.string.sources_action_disable)
    } else {
        stringResource(R.string.sources_action_enable)
    }
    val pinnedLabel = if (item.pinnedIndex >= 0) {
        stringResource(R.string.sources_action_unpin)
    } else {
        stringResource(R.string.sources_action_pin)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.enabled) 1f else 0.45f)
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("source_row_${item.source.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.getSourceIcon(item.source.id),
                    contentDescription = stringResource(R.string.sources_row_icon_cd),
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.source.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.source.contentRating >= 2) {
                        Spacer(modifier = Modifier.width(6.dp))
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.updateAvailableVersion != null) {
                Spacer(modifier = Modifier.width(8.dp))
                UpdatePill(onClick = { onUpdate() })
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("source_row_menu_${item.source.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.sources_menu_cd),
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(enabledLabel) },
                        onClick = {
                            menuExpanded = false
                            onToggleEnabled()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(pinnedLabel) },
                        onClick = {
                            menuExpanded = false
                            onTogglePinned()
                        },
                    )
                    if (item.isUserInstalled) {
                        HorizontalDivider(color = CardBorderDark)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sources_action_uninstall), color = AnimeRed) },
                            onClick = {
                                menuExpanded = false
                                onRequestUninstall()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatePill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(AnimeRed)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
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

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Language,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.sources_empty_title),
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.sources_empty_subtitle),
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .background(AnimeRed)
                .clickable(onClick = onAdd)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.sources_add_cd),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
            )
        }
    }
}