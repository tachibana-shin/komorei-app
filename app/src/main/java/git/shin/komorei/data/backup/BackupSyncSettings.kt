package git.shin.komorei.data.backup

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** User-selectable cadence for the optional WorkManager Drive sync. */
enum class BackupSyncInterval(
    val hours: Long,
) {
    SIX_HOURS(6),
    TWELVE_HOURS(12),
    DAILY(24),
    TWO_DAYS(48),
    WEEKLY(168),
}

enum class BackupSyncError {
    AUTH_REQUIRED,
    REMOTE_CHANGED,
    NETWORK,
    UNKNOWN,
}

data class BackupSyncSettings(
    val enabled: Boolean = false,
    val interval: BackupSyncInterval = BackupSyncInterval.DAILY,
    val lastSyncAt: Long? = null,
    /** Remote Drive modifiedTime after the last successful upload. */
    val lastRemoteModifiedTime: String? = null,
    val lastError: BackupSyncError? = null,
    /** Sections used by the last successful Drive upload. */
    val backupOptions: BackupOptions = BackupOptions(),
)

/** Small SharedPreferences store; the actual work is scheduled by [BackupSyncScheduler]. */
@Singleton
class BackupSyncSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<BackupSyncSettings> = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(enabled = enabled)
    }

    fun setInterval(interval: BackupSyncInterval) {
        prefs.edit().putString(KEY_INTERVAL, interval.name).apply()
        _settings.value = _settings.value.copy(interval = interval)
    }

    fun setBackupOptions(options: BackupOptions) {
        prefs
            .edit()
            .putBoolean(KEY_LIBRARY, options.includeLibrary)
            .putBoolean(KEY_HISTORY, options.includeHistory)
            .putBoolean(KEY_SOURCE_STATE, options.includeSourceState)
            .putBoolean(KEY_SOURCE_DEFAULTS, options.includeSourceDefaults)
            .putBoolean(KEY_SEARCH_HISTORY, options.includeSearchHistory)
            .putBoolean(KEY_USER_SOURCES, options.includeUserSources)
            .apply()
        _settings.value = _settings.value.copy(backupOptions = options)
    }

    fun markSuccess(
        remoteModifiedTime: String?,
        syncedAt: Long = System.currentTimeMillis(),
    ) {
        prefs
            .edit()
            .putLong(KEY_LAST_SYNC, syncedAt)
            .putString(KEY_LAST_REMOTE_MODIFIED, remoteModifiedTime)
            .remove(KEY_ERROR)
            .apply()
        _settings.value =
            _settings.value.copy(
                lastSyncAt = syncedAt,
                lastRemoteModifiedTime = remoteModifiedTime,
                lastError = null,
            )
    }

    fun markError(error: BackupSyncError) {
        prefs.edit().putString(KEY_ERROR, error.name).apply()
        _settings.value = _settings.value.copy(lastError = error)
    }

    private fun read(): BackupSyncSettings =
        BackupSyncSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            interval =
                prefs
                    .getString(KEY_INTERVAL, null)
                    ?.let { name -> BackupSyncInterval.entries.firstOrNull { it.name == name } }
                    ?: BackupSyncInterval.DAILY,
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L },
            lastRemoteModifiedTime = prefs.getString(KEY_LAST_REMOTE_MODIFIED, null),
            lastError =
                prefs
                    .getString(KEY_ERROR, null)
                    ?.let { name -> BackupSyncError.entries.firstOrNull { it.name == name } },
            backupOptions =
                BackupOptions(
                    includeLibrary = prefs.getBoolean(KEY_LIBRARY, true),
                    includeHistory = prefs.getBoolean(KEY_HISTORY, true),
                    includeSourceState = prefs.getBoolean(KEY_SOURCE_STATE, true),
                    includeSourceDefaults = prefs.getBoolean(KEY_SOURCE_DEFAULTS, false),
                    includeSearchHistory = prefs.getBoolean(KEY_SEARCH_HISTORY, false),
                    includeUserSources = prefs.getBoolean(KEY_USER_SOURCES, false),
                ),
        )

    private companion object {
        const val PREFS_NAME = "backup_sync"
        const val KEY_ENABLED = "enabled"
        const val KEY_INTERVAL = "interval"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_LAST_REMOTE_MODIFIED = "last_remote_modified"
        const val KEY_ERROR = "last_error"
        const val KEY_LIBRARY = "include_library"
        const val KEY_HISTORY = "include_history"
        const val KEY_SOURCE_STATE = "include_source_state"
        const val KEY_SOURCE_DEFAULTS = "include_source_defaults"
        const val KEY_SEARCH_HISTORY = "include_search_history"
        const val KEY_USER_SOURCES = "include_user_sources"
    }
}
