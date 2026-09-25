package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.backup.BackupOptions
import git.shin.komorei.data.backup.BackupSyncError
import git.shin.komorei.data.backup.BackupSyncInterval
import git.shin.komorei.data.backup.BackupSyncSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupSyncSettingsTest {
    private lateinit var store: BackupSyncSettingsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("backup_sync", Context.MODE_PRIVATE).edit().clear().commit()
        store = BackupSyncSettingsStore(context)
    }

    @Test
    fun defaultsToDisabledDailySync() {
        val settings = store.settings.value
        assertFalse(settings.enabled)
        assertEquals(BackupSyncInterval.DAILY, settings.interval)
        assertNull(settings.lastSyncAt)
    }

    @Test
    fun persistsToggleIntervalAndLastSuccessfulSync() {
        store.setEnabled(true)
        store.setInterval(BackupSyncInterval.WEEKLY)
        store.setBackupOptions(BackupOptions(includeUserSources = true))
        store.markSuccess("2026-09-25T00:00:00Z", syncedAt = 1234L)

        val reloaded = BackupSyncSettingsStore(ApplicationProvider.getApplicationContext())
        assertEquals(true, reloaded.settings.value.enabled)
        assertEquals(BackupSyncInterval.WEEKLY, reloaded.settings.value.interval)
        assertEquals(1234L, reloaded.settings.value.lastSyncAt)
        assertEquals("2026-09-25T00:00:00Z", reloaded.settings.value.lastRemoteModifiedTime)
        assertEquals(true, reloaded.settings.value.backupOptions.includeUserSources)
    }

    @Test
    fun recordsBackgroundErrors() {
        store.markError(BackupSyncError.REMOTE_CHANGED)
        assertEquals(BackupSyncError.REMOTE_CHANGED, store.settings.value.lastError)
    }
}
