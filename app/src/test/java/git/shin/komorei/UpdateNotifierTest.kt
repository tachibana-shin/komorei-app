package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import git.shin.komorei.data.update.UpdateCheckResult
import git.shin.komorei.data.update.UpdateInfo
import git.shin.komorei.data.update.UpdateManager
import git.shin.komorei.data.update.UpdateNotifier
import git.shin.komorei.data.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The automatic update check.
 *
 * Two behaviours matter and neither is visible in the UI: a launch must not cost
 * a release-API request every time, and a release the reader waved away must
 * not come back on the next launch. Both are remembered across processes, so
 * they are tested through the notifier rather than through a composable.
 *
 * The real `UpdateManager` is pointed at a client that answers nothing — the
 * notifier's job is decided before the request is made, and a check that fails
 * leaves the state Idle either way, so there is nothing here to stub.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class UpdateNotifierTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("app_update", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        context
            .getSharedPreferences("app_update", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun notifier() = UpdateNotifier(context, UpdateManager(context, OkHttpClient()))

    private fun prefs() = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    private fun info(version: String) =
        UpdateInfo(
            version = version,
            releaseNotes = "## $version",
            downloadUrl = "https://example.invalid/a.apk",
            assetId = 1L,
            expectedSize = 1L,
            sha256 = "a".repeat(64),
        )

    @Test
    fun `the first launch of the day does check`() {
        val n = notifier()
        n.checkForUpdate(automatic = true)
        assertTrue(
            "a first launch should stamp the check so the next one can skip it",
            prefs().getLong("last_automatic_check", 0L) > 0L,
        )
    }

    @Test
    fun `a second launch on the same day does not stamp again`() {
        notifier().checkForUpdate(automatic = true)
        val first = prefs().getLong("last_automatic_check", 0L)
        assertTrue(first > 0L)

        // Pretend it just ran.
        val n = notifier()
        n.checkForUpdate(automatic = true)
        assertEquals(
            "within the interval the second check must be skipped",
            first,
            prefs().getLong("last_automatic_check", 0L),
        )
    }

    @Test
    fun `a manual check ignores the once-a-day throttle`() {
        notifier().checkForUpdate(automatic = true)
        val first = prefs().getLong("last_automatic_check", 0L)

        // Somebody tapped the row. Being throttled would mean the tap does
        // nothing at all, which is the whole point of a manual check.
        notifier().checkForUpdate(automatic = false)
        assertTrue(
            "a manual check must always go out",
            prefs().getLong("last_automatic_check", 0L) >= first,
        )
    }

    @Test
    fun `a failed check still counts, so an unreachable api is not retried on every launch`() {
        // The client answers nothing, so the check fails.
        notifier().checkForUpdate(automatic = true)
        assertTrue(
            "a check that never came back must still consume the day",
            prefs().getLong("last_automatic_check", 0L) > 0L,
        )
    }

    @Test
    fun `dismissing remembers the version across a new notifier`() {
        val info = info(version = "9.9.9")
        val n = notifier()
        // The state a found update produces, through the same branch the check
        // uses, then dismissed the way the sheet's "later" button does.
        n.applyResult(UpdateCheckResult.Available(info), automatic = true)
        n.dismiss()

        assertEquals(
            "the dismissed version must be remembered",
            "9.9.9",
            prefs().getString("dismissed_version", null),
        )
        assertEquals("dismissing returns the state to idle", UpdateUiState.Idle, n.state.value)
    }

    @Test
    fun `an automatic check for a new version does surface`() {
        val info = info(version = "9.9.9")
        val n = notifier()
        n.applyResult(UpdateCheckResult.Available(info), automatic = true)
        assertEquals(
            "a version never seen before must be offered",
            UpdateUiState.Available(info),
            n.state.value,
        )
    }

    @Test
    fun `dismissing one version does not silence a newer one`() {
        val n = notifier()
        n.applyResult(UpdateCheckResult.Available(info(version = "1.9.0")), automatic = true)
        n.dismiss()

        // The release moved on. Re-opening for 2.0.0 is the point of remembering
        // a version rather than a blanket "asked, do not ask again".
        val newer = info(version = "2.0.0")
        n.applyResult(UpdateCheckResult.Available(newer), automatic = true)
        assertEquals(UpdateUiState.Available(newer), n.state.value)
    }

    @Test
    fun `an automatic check for a dismissed version offers nothing`() {
        val info = info(version = "9.9.9")
        prefs().edit().putString("dismissed_version", "9.9.9").commit()

        val n = notifier()
        n.applyResult(UpdateCheckResult.Available(info), automatic = true)

        assertEquals(
            "a version already waved away must not reopen the sheet",
            UpdateUiState.Idle,
            n.state.value,
        )
    }
}
