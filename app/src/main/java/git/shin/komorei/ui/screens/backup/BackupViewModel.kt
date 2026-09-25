package git.shin.komorei.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.R
import git.shin.komorei.data.backup.BackupFileInfo
import git.shin.komorei.data.backup.BackupOptions
import git.shin.komorei.data.backup.BackupRepository
import git.shin.komorei.data.backup.BackupSyncInterval
import git.shin.komorei.data.backup.BackupSyncScheduler
import git.shin.komorei.data.backup.BackupSyncSettingsStore
import git.shin.komorei.data.backup.DriveApiException
import git.shin.komorei.data.backup.DriveBackupApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupUiState(
    val backups: List<BackupFileInfo> = emptyList(),
    val busy: Boolean = false,
    val driveConnected: Boolean = false,
    val lastDriveSyncAt: Long? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    private val driveApi: DriveBackupApi,
    private val syncSettingsStore: BackupSyncSettingsStore,
    private val syncScheduler: BackupSyncScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    val syncSettings = syncSettingsStore.settings

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val backups = withContext(Dispatchers.IO) { repository.listLocalBackups() }
            _state.value = _state.value.copy(backups = backups)
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        syncScheduler.setEnabled(enabled)
        if (enabled) syncScheduler.runNow()
    }

    fun setSyncInterval(interval: BackupSyncInterval) {
        syncScheduler.setInterval(interval)
    }

    fun runBackgroundSyncNow() {
        syncScheduler.runNow()
    }

    fun createBackup(name: String?, options: BackupOptions) = runBusy(R.string.backup_created) {
        repository.createLocalBackup(name, options)
    }

    fun importUri(uri: android.net.Uri) = runBusy(R.string.backup_imported) {
        repository.importUri(uri)
    }

    fun importDriveBackup(bytes: ByteArray) = runBusy(R.string.backup_downloaded) {
        repository.importBytes(bytes, "drive")
    }

    fun exportUri(fileName: String, uri: android.net.Uri) = runBusy(R.string.backup_exported) {
        repository.exportToUri(fileName, uri)
    }

    fun deleteBackup(fileName: String) = runBusy(R.string.backup_deleted) {
        repository.delete(fileName)
    }

    fun restoreBackup(fileName: String) = runBusy(R.string.backup_restored) {
        repository.restore(fileName)
    }

    fun markDriveConnected() {
        _state.value = _state.value.copy(driveConnected = true)
    }

    fun forgetDrive() {
        _state.value = _state.value.copy(driveConnected = false, lastDriveSyncAt = null)
    }

    /** Uploads a fresh local snapshot, replacing the appDataFolder file if present. */
    fun uploadToDrive(accessToken: String, options: BackupOptions = BackupOptions()) = runBusy(
        R.string.backup_drive_uploaded,
    ) {
        val local = repository.createLocalBackup("drive", options)
        val bytes = repository.readBytes(local.fileName)
        val existing = driveApi.findBackup(accessToken)
        val remote = try {
            driveApi.uploadBackup(accessToken, bytes, existing?.id)
        } catch (error: DriveApiException) {
            if (existing == null || error.statusCode != 404) throw error
            // A stale file id can be deleted from another device; recreate it.
            driveApi.uploadBackup(accessToken, bytes, existingId = null)
        }
        syncSettingsStore.setBackupOptions(options)
        syncSettingsStore.markSuccess(remote.modifiedTime)
        _state.value = _state.value.copy(
            driveConnected = true,
            lastDriveSyncAt = System.currentTimeMillis(),
        )
    }

    /** Downloads the remote file into the local list; it is never restored implicitly. */
    fun downloadFromDrive(accessToken: String) = runBusy(R.string.backup_downloaded) {
        val remote = driveApi.findBackup(accessToken)
            ?: throw DriveBackupNotFoundException()
        val bytes = driveApi.downloadBackup(accessToken, remote.id)
            ?: throw DriveBackupNotFoundException()
        repository.importBytes(bytes, "drive")
        _state.value = _state.value.copy(
            driveConnected = true,
            lastDriveSyncAt = System.currentTimeMillis(),
        )
    }

    private fun <T> runBusy(message: Int, block: suspend () -> T) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            try {
                withContext(Dispatchers.IO) { block() }
                val backups = withContext(Dispatchers.IO) { repository.listLocalBackups() }
                _state.value = _state.value.copy(backups = backups, busy = false)
                _messages.send(message)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(busy = false)
                _messages.send(
                    if (error is DriveBackupNotFoundException ||
                        (error is DriveApiException && error.statusCode == 404)
                    ) {
                        R.string.backup_drive_empty
                    } else {
                        R.string.backup_operation_failed
                    },
                )
            }
        }
    }
}

private class DriveBackupNotFoundException : Exception()
