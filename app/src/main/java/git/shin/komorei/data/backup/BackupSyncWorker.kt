package git.shin.komorei.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Hilt bridge for a default-constructor WorkManager worker. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupSyncEntryPoint {
    fun backupRepository(): BackupRepository
    fun driveBackupApi(): DriveBackupApi
    fun syncSettingsStore(): BackupSyncSettingsStore
}

/**
 * Uploads a fresh logical backup when the user's background-sync setting is
 * enabled. A remote modifiedTime baseline prevents a second device from
 * silently overwriting a backup that changed elsewhere; that case is surfaced
 * in the Backup screen for manual download/restore.
 */
class BackupSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BackupSyncEntryPoint::class.java,
        )
        val settingsStore = entryPoint.syncSettingsStore()
        val settings = settingsStore.settings.value
        if (!settings.enabled) return Result.success()

        val repository = entryPoint.backupRepository()
        val driveApi = entryPoint.driveBackupApi()
        val tokenResult = GoogleDriveAuthorization.authorizeForBackground(applicationContext)
        val token = tokenResult.getOrElse { error ->
            if (error is CancellationException) throw error
            settingsStore.markError(BackupSyncError.AUTH_REQUIRED)
            return Result.success()
        }
        if (!settingsStore.settings.value.enabled) return Result.success()

        return try {
            val bytes = repository.createPayloadBytes(options = settings.backupOptions)
            val existing = driveApi.findBackup(token)
            val remote = if (existing == null) {
                driveApi.uploadBackup(token, bytes, existingId = null)
            } else {
                val baseline = settings.lastRemoteModifiedTime
                if (baseline == null || baseline != existing.modifiedTime) {
                    settingsStore.markError(BackupSyncError.REMOTE_CHANGED)
                    return Result.success()
                }
                driveApi.uploadBackup(token, bytes, existing.id)
            }
            settingsStore.markSuccess(remote.modifiedTime)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: DriveApiException) {
            when {
                error.statusCode == 401 || error.statusCode == 403 -> {
                    settingsStore.markError(BackupSyncError.AUTH_REQUIRED)
                    Result.success()
                }
                error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500 -> {
                    settingsStore.markError(BackupSyncError.NETWORK)
                    Result.retry()
                }
                else -> {
                    settingsStore.markError(BackupSyncError.UNKNOWN)
                    Result.success()
                }
            }
        } catch (error: IOException) {
            settingsStore.markError(BackupSyncError.NETWORK)
            Result.retry()
        } catch (_: Exception) {
            settingsStore.markError(BackupSyncError.UNKNOWN)
            Result.success()
        }
    }
}
