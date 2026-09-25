package git.shin.komorei.ui.screens.backup

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import git.shin.komorei.R
import git.shin.komorei.data.backup.BackupFileInfo
import git.shin.komorei.data.backup.BackupOptions
import git.shin.komorei.data.backup.BackupSyncError
import git.shin.komorei.data.backup.BackupSyncInterval
import git.shin.komorei.data.backup.BackupSyncSettings
import git.shin.komorei.data.backup.GoogleDriveAuthorization
import git.shin.komorei.ui.components.ShimmerLoadingRow
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.SurfaceVariantDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus
import java.text.DateFormat
import java.util.Date

/** Local backup list + Google Drive backup/restore, modeled on Aidoku's BackupsView. */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncSettings by viewModel.syncSettings.collectAsStateWithLifecycle()
    var showOptions by remember { mutableStateOf(false) }
    var backupName by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(BackupOptions()) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupFileInfo?>(null) }
    var pendingDelete by remember { mutableStateOf<BackupFileInfo?>(null) }
    var pendingUploadToken by remember { mutableStateOf<String?>(null) }
    var authAction by remember { mutableStateOf<DriveAuthAction?>(null) }
    val activity = context as? Activity

    fun handleDriveToken(token: String) {
        viewModel.markDriveConnected()
        when (authAction) {
            DriveAuthAction.CONNECT -> authAction = null
            DriveAuthAction.UPLOAD -> pendingUploadToken = token
            DriveAuthAction.DOWNLOAD -> {
                authAction = null
                viewModel.downloadFromDrive(token)
            }
            null -> Unit
        }
    }

    val openDocument =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(viewModel::importUri) }
    val createDocument =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            val fileName = pendingExport
            pendingExport = null
            if (uri != null && fileName != null) viewModel.exportUri(fileName, uri)
        }
    val authorizationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (activity != null) {
                GoogleDriveAuthorization.handleResolutionResult(activity, result.data) { authResult ->
                    authResult.fold(
                        onSuccess = { token -> handleDriveToken(token) },
                        onFailure = { Toast.makeText(context, R.string.backup_drive_auth_failed, Toast.LENGTH_LONG).show() },
                    )
                }
            }
        }

    fun authorize(action: DriveAuthAction) {
        val currentActivity = activity
        if (currentActivity == null) {
            Toast.makeText(context, R.string.backup_drive_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        authAction = action
        GoogleDriveAuthorization.authorize(currentActivity, authorizationLauncher) { result ->
            result.fold(
                onSuccess = { token -> handleDriveToken(token) },
                onFailure = {
                    authAction = null
                    Toast.makeText(context, R.string.backup_drive_auth_failed, Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .testTag("backup_screen"),
    ) {
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
                        .testTag("backup_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.backup_title),
                color = TextPrimary,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Black,
            )
        }

        if (state.busy) ShimmerLoadingRow()

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_description),
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )

            DriveSection(
                connected = state.driveConnected,
                configured = GoogleDriveAuthorization.isConfigured(),
                lastSyncAt = state.lastDriveSyncAt,
                onConnect = { authorize(DriveAuthAction.CONNECT) },
                onUpload = { authorize(DriveAuthAction.UPLOAD) },
                onDownload = { authorize(DriveAuthAction.DOWNLOAD) },
                onForget = viewModel::forgetDrive,
            )
            Spacer(Modifier.height(12.dp))
            BackgroundSyncSection(
                settings = syncSettings,
                onEnabledChange = viewModel::setAutoSyncEnabled,
                onIntervalChange = viewModel::setSyncInterval,
                onRunNow = viewModel::runBackgroundSyncNow,
            )

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.backup_local_section),
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.backup_count, state.backups.size),
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { showOptions = true },
                    modifier =
                        Modifier
                            .weight(1f)
                            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.02f)
                            .testTag("backup_create"),
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.backup_create))
                }
                OutlinedButton(
                    onClick = { openDocument.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.02f)
                            .testTag("backup_import"),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.backup_import))
                }
            }

            if (state.backups.isEmpty()) {
                Text(
                    text = stringResource(R.string.backup_empty),
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp)
                            .testTag("backup_empty"),
                )
            } else {
                Spacer(Modifier.height(12.dp))
                state.backups.forEach { backup ->
                    BackupRow(
                        backup = backup,
                        onRestore = { pendingRestore = backup },
                        onExport = {
                            pendingExport = backup.fileName
                            createDocument.launch(backup.fileName)
                        },
                        onDelete = { pendingDelete = backup },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (showOptions) {
        BackupOptionsDialog(
            name = backupName,
            onNameChange = { backupName = it },
            options = options,
            onOptionsChange = { options = it },
            onDismiss = { showOptions = false },
            onConfirm = {
                showOptions = false
                viewModel.createBackup(backupName, options)
                backupName = ""
            },
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = CardDark,
            title = { Text(stringResource(R.string.backup_restore_title), color = TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.backup_restore_message),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        viewModel.restoreBackup(backup.fileName)
                    },
                    modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                ) {
                    Text(stringResource(R.string.backup_restore), color = AnimeRed)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestore = null },
                    modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                ) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) }
            },
        )
    }

    pendingDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = CardDark,
            title = { Text(stringResource(R.string.backup_delete_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.backup_delete_message), color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteBackup(backup.fileName)
                    },
                    modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                ) { Text(stringResource(R.string.backup_delete), color = AnimeRed) }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                ) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) }
            },
        )
    }

    pendingUploadToken?.let { token ->
        AlertDialog(
            onDismissRequest = {
                pendingUploadToken = null
                authAction = null
            },
            containerColor = CardDark,
            title = { Text(stringResource(R.string.backup_drive_upload_title), color = TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.backup_drive_upload_message),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUploadToken = null
                        authAction = null
                        viewModel.uploadToDrive(token, options)
                    },
                    modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                ) { Text(stringResource(R.string.backup_drive_upload), color = AnimeRed) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingUploadToken = null
                        authAction = null
                    },
                    modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
                ) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) }
            },
        )
    }
}

private enum class DriveAuthAction { CONNECT, UPLOAD, DOWNLOAD }

@Composable
private fun DriveSection(
    connected: Boolean,
    configured: Boolean,
    lastSyncAt: Long?,
    onConnect: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onForget: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .padding(14.dp)
                .testTag("backup_drive_section"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (connected) Icons.Default.CloudDone else Icons.Default.Cloud,
                contentDescription = null,
                tint = if (connected) git.shin.komorei.ui.theme.SuccessGreen else AnimeRed,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_drive_title),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        when {
                            !configured -> stringResource(R.string.backup_drive_not_configured)
                            connected && lastSyncAt != null ->
                                stringResource(
                                    R.string.backup_drive_last_sync,
                                    DateFormat
                                        .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(lastSyncAt)),
                                )
                            connected -> stringResource(R.string.backup_drive_connected)
                            else -> stringResource(R.string.backup_drive_disconnected)
                        },
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            if (connected) {
                IconButton(
                    onClick = onForget,
                    modifier =
                        Modifier
                            .tvFocus(shape = CircleShape, scale = 1.1f)
                            .testTag("backup_drive_forget"),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.backup_drive_forget),
                        tint = TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.backup_drive_description),
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (!configured) {
            Text(
                text = stringResource(R.string.backup_drive_setup_hint),
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        } else if (!connected) {
            OutlinedButton(
                onClick = onConnect,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.02f)
                        .testTag("backup_drive_connect"),
            ) { Text(stringResource(R.string.backup_drive_connect)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUpload,
                    modifier =
                        Modifier
                            .weight(1f)
                            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.02f)
                            .testTag("backup_drive_upload"),
                ) { Text(stringResource(R.string.backup_drive_upload)) }
                OutlinedButton(
                    onClick = onDownload,
                    modifier =
                        Modifier
                            .weight(1f)
                            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.02f)
                            .testTag("backup_drive_download"),
                ) { Text(stringResource(R.string.backup_drive_download)) }
            }
        }
    }
}

@Composable
private fun BackgroundSyncSection(
    settings: BackupSyncSettings,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (BackupSyncInterval) -> Unit,
    onRunNow: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .padding(14.dp)
                .testTag("backup_background_sync_section"),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.0f)
                    .clickable { onEnabledChange(!settings.enabled) }
                    .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = if (settings.enabled) git.shin.komorei.ui.theme.SuccessGreen else TextMuted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_auto_sync),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        if (settings.enabled) {
                            stringResource(R.string.backup_auto_sync_enabled)
                        } else {
                            stringResource(R.string.backup_auto_sync_disabled)
                        },
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = null)
        }

        if (settings.enabled) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.backup_sync_interval),
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BackupSyncInterval.entries.forEach { interval ->
                    SyncIntervalChip(
                        interval = interval,
                        selected = settings.interval == interval,
                        onClick = { onIntervalChange(interval) },
                    )
                }
            }
            settings.lastSyncAt?.let { lastSync ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(
                            R.string.backup_last_sync,
                            DateFormat
                                .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(lastSync)),
                        ),
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            settings.lastError?.let { error ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(error.messageRes()),
                    color = AnimeRed,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRunNow,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.02f)
                        .testTag("backup_sync_now"),
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.backup_sync_now))
            }
        }
    }
}

@Composable
private fun SyncIntervalChip(
    interval: BackupSyncInterval,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label =
        when (interval) {
            BackupSyncInterval.SIX_HOURS -> stringResource(R.string.backup_every_6_hours)
            BackupSyncInterval.TWELVE_HOURS -> stringResource(R.string.backup_every_12_hours)
            BackupSyncInterval.DAILY -> stringResource(R.string.backup_daily)
            BackupSyncInterval.TWO_DAYS -> stringResource(R.string.backup_every_2_days)
            BackupSyncInterval.WEEKLY -> stringResource(R.string.backup_weekly)
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) AnimeRed else SurfaceVariantDark)
                .tvFocus(shape = RoundedCornerShape(9.dp), scale = 1.0f)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("backup_interval_${interval.name.lowercase()}"),
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
        )
    }
}

private fun BackupSyncError.messageRes(): Int =
    when (this) {
        BackupSyncError.AUTH_REQUIRED -> R.string.backup_sync_auth_required
        BackupSyncError.REMOTE_CHANGED -> R.string.backup_sync_remote_changed
        BackupSyncError.NETWORK -> R.string.backup_sync_network_error
        BackupSyncError.UNKNOWN -> R.string.backup_sync_unknown_error
    }

@Composable
private fun BackupRow(
    backup: BackupFileInfo,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("backup_row_${backup.fileName}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backup.name ?: backup.fileName,
                    color = if (backup.valid) TextPrimary else AnimeRed,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        DateFormat
                            .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(backup.createdAt)),
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
            Text(
                text =
                    android.text.format.Formatter.formatShortFileSize(
                        LocalContext.current,
                        backup.sizeBytes,
                    ),
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text =
                stringResource(
                    R.string.backup_counts,
                    backup.counts.library,
                    backup.counts.history,
                ),
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = onRestore,
                enabled = backup.valid,
                modifier =
                    Modifier
                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.02f)
                        .testTag("backup_restore_${backup.fileName}"),
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.backup_restore))
            }
            TextButton(
                onClick = onExport,
                enabled = backup.valid,
                modifier =
                    Modifier
                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.02f)
                        .testTag("backup_export_${backup.fileName}"),
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.backup_export))
            }
            TextButton(
                onClick = onDelete,
                modifier =
                    Modifier
                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.02f)
                        .testTag("backup_delete_${backup.fileName}"),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.backup_delete), tint = AnimeRed)
            }
        }
    }
}

@Composable
private fun BackupOptionsDialog(
    name: String,
    onNameChange: (String) -> Unit,
    options: BackupOptions,
    onOptionsChange: (BackupOptions) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text(stringResource(R.string.backup_options_title), color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.backup_name)) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.0f)
                            .testTag("backup_name"),
                )
                Spacer(Modifier.height(4.dp))
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_library),
                    checked = options.includeLibrary,
                    onCheckedChange = { onOptionsChange(options.copy(includeLibrary = it)) },
                )
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_history),
                    checked = options.includeHistory,
                    onCheckedChange = { onOptionsChange(options.copy(includeHistory = it)) },
                )
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_source_state),
                    checked = options.includeSourceState,
                    onCheckedChange = { onOptionsChange(options.copy(includeSourceState = it)) },
                )
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_source_defaults),
                    checked = options.includeSourceDefaults,
                    onCheckedChange = { onOptionsChange(options.copy(includeSourceDefaults = it)) },
                )
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_search_history),
                    checked = options.includeSearchHistory,
                    onCheckedChange = { onOptionsChange(options.copy(includeSearchHistory = it)) },
                )
                BackupOptionRow(
                    label = stringResource(R.string.backup_option_user_sources),
                    checked = options.includeUserSources,
                    onCheckedChange = { onOptionsChange(options.copy(includeUserSources = it)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
            ) { Text(stringResource(R.string.backup_create), color = AnimeRed) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f),
            ) { Text(stringResource(R.string.source_settings_cancel), color = TextSecondary) }
        },
    )
}

@Composable
private fun BackupOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.0f)
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}
