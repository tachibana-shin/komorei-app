package git.shin.komorei.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the unique WorkManager jobs used by the optional background Drive sync. */
@Singleton
class BackupSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: BackupSyncSettingsStore,
) {
    /**
     * Reconcile persisted state after process death without eagerly creating
     * WorkManager in a disabled/test-only process. If a job was already
     * initialized before the process died, still remove it when sync is off.
     */
    fun reconcileCurrentSettings() {
        val settings = settingsStore.settings.value
        if (settings.enabled) schedule(settings.interval) else cancelIfInitialized()
    }

    fun applyCurrentSettings() {
        val settings = settingsStore.settings.value
        if (settings.enabled) schedule(settings.interval) else cancel()
    }

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
        applyCurrentSettings()
    }

    fun setInterval(interval: BackupSyncInterval) {
        settingsStore.setInterval(interval)
        if (settingsStore.settings.value.enabled) schedule(interval)
    }

    /** Trigger an early run after the user enables sync or taps Sync now. */
    fun runNow() {
        if (!settingsStore.settings.value.enabled) return
        val request =
            OneTimeWorkRequestBuilder<BackupSyncWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
        workManager().enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun schedule(interval: BackupSyncInterval) {
        val request =
            PeriodicWorkRequestBuilder<BackupSyncWorker>(
                interval.hours,
                TimeUnit.HOURS,
            ).setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
        workManager().enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun cancel() {
        workManager().cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager().cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    private fun cancelIfInitialized() {
        val workManager =
            try {
                WorkManager.getInstance(context)
            } catch (_: IllegalStateException) {
                return
            }
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    private fun workManager(): WorkManager =
        try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(
                context,
                Configuration
                    .Builder()
                    .setMinimumLoggingLevel(android.util.Log.INFO)
                    .build(),
            )
            WorkManager.getInstance(context)
        }

    private fun constraints() =
        Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

    companion object {
        const val PERIODIC_WORK_NAME = "komorei-drive-sync-periodic"
        const val ONE_TIME_WORK_NAME = "komorei-drive-sync-now"
    }
}
