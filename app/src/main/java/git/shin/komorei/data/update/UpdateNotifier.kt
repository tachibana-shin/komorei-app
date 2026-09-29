package git.shin.komorei.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the update state for the whole app and runs the checks.
 *
 * This is a singleton rather than state on the settings view model for two
 * reasons that both showed up as bugs:
 *
 * The automatic check happens on launch, from whatever screen the reader lands
 * on, so the state has to outlive any one view model — a view model scoped to
 * Settings would drop the "an update is waiting" flag the moment the reader left
 * the tab, and the sheet would never appear.
 *
 * A dismissed sheet must not come back on the next launch, and a dismissed
 * dialog must not either. Both need the choice remembered across processes, so
 * the version that was waved away is persisted rather than kept in a field.
 */
@Singleton
class UpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateManager: UpdateManager,
) {
    private val prefs = UpdatePrefs(context)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /**
     * How a check ended, so the settings row can report it.
     *
     * The row has always said something when tapped — "already up to date",
     * "could not reach the release" — and that is worth keeping. Emitting the
     * outcome separately is what lets this be the only state holder: the view
     * model collects the outcome for a toast instead of running its own second
     * check, so a manual tap and the launch-time check cannot disagree about
     * what the latest version is.
     */
    private val _outcomes = MutableSharedFlow<UpdateOutcome>(extraBufferCapacity = 4)
    val outcomes: SharedFlow<UpdateOutcome> = _outcomes.asSharedFlow()

    /**
     * A check in flight, so a launch-time check and a tap on the settings row
     * cannot both run — the second would only duplicate a request already on the
     * wire and race it to write the state.
     */
    private val inFlight = MutableStateFlow(false)

    /** Survives the screen being recreated, unlike a view-model field. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Checks for a newer release.
     *
     * [automatic] checks are the launch-time ones and stay quiet unless there is
     * something to offer: no toast, no error, and a release the reader already
     * waved away is not offered again. A [manual] check is somebody tapping
     * "check for updates" and expecting to be told either way.
     */
    fun checkForUpdate(automatic: Boolean = false) {
        if (inFlight.value) return
        val lastCheck = prefs.lastAutomaticCheck()
        if (automatic && !isDueForAutomaticCheck(lastCheck)) return

        inFlight.value = true
        if (!automatic) _state.value = UpdateUiState.Checking
        // Stamped before the request, not after: a check that never comes back
        // must still count, or an unreachable release API would be retried on
        // every single launch instead of once a day.
        prefs.setLastAutomaticCheck(System.currentTimeMillis())

        scope.launch {
            try {
                updateManager.checkForUpdate().fold(
                    onSuccess = { result -> applyResult(result, automatic) },
                    onFailure = {
                        _state.value = UpdateUiState.Idle
                        if (!automatic) _outcomes.tryEmit(UpdateOutcome.Failed)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed check is not worth surfacing on its own: the row in
                // Settings stays tappable and reports the result of the next one.
                _state.value = UpdateUiState.Idle
                if (!automatic) _outcomes.tryEmit(UpdateOutcome.Failed)
            } finally {
                inFlight.value = false
            }
        }
    }

    fun dismiss() {
        val available = _state.value as? UpdateUiState.Available ?: return
        rememberDismissed(available.info.version)
        _state.value = UpdateUiState.Idle
    }

    /**
     * Turns a check result into state and outcomes.
     *
     * Split out of [checkForUpdate] so the decision the reader actually sees —
     * offer it, or stay quiet because this exact version was already waved away
     * — is reachable without a release API to answer. The check calls this, so
     * the tests drive the same branch the launch does rather than a copy of it.
     */
    internal fun applyResult(
        result: UpdateCheckResult,
        automatic: Boolean,
    ) {
        when (result) {
            is UpdateCheckResult.UpToDate -> {
                _state.value = UpdateUiState.Idle
                if (!automatic) _outcomes.tryEmit(UpdateOutcome.UpToDate)
            }

            is UpdateCheckResult.Available -> {
                if (automatic && isDismissed(result.info.version)) {
                    // Already waved away. The row in Settings still reflects that a
                    // newer version exists; this is only about not reopening the
                    // sheet on the reader's next launch.
                    _state.value = UpdateUiState.Idle
                } else {
                    _state.value = UpdateUiState.Available(result.info)
                    if (!automatic) _outcomes.tryEmit(UpdateOutcome.Available)
                }
            }
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        if (_state.value is UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Downloading(info, 0)
        scope.launch {
            try {
                updateManager.downloadAndInstall(info) { progress ->
                    _state.value = UpdateUiState.Downloading(info, progress)
                }
                _outcomes.tryEmit(UpdateOutcome.InstallStarted)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = UpdateUiState.Available(info)
                _outcomes.tryEmit(UpdateOutcome.InstallFailed)
            }
        }
    }

    /**
     * Whether a launch-time check is worth doing: not on the same day as the last
     * one. A reader who opens the app several times a day should not pay for a
     * release-API request on each of them, and a release does not appear and
     * change within a day.
     */
    private fun isDueForAutomaticCheck(lastCheck: Long): Boolean {
        if (lastCheck == 0L) return true
        return System.currentTimeMillis() - lastCheck >= AUTOMATIC_CHECK_INTERVAL_MS
    }

    private fun isDismissed(version: String): Boolean = prefs.dismissedVersion() == version

    private fun rememberDismissed(version: String) {
        prefs.setDismissedVersion(version)
    }

    private companion object {
        const val AUTOMATIC_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}

/** The two things the update flow has to remember across processes. */
internal class UpdatePrefs(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    fun lastAutomaticCheck(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    fun setLastAutomaticCheck(value: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK, value).apply()
    }

    fun dismissedVersion(): String? = prefs.getString(KEY_DISMISSED, null)

    fun setDismissedVersion(value: String) {
        prefs.edit().putString(KEY_DISMISSED, value).apply()
    }

    private companion object {
        const val KEY_LAST_CHECK = "last_automatic_check"
        const val KEY_DISMISSED = "dismissed_version"
    }
}

/** How a check or an install ended, for whoever wants to report it to the reader. */
enum class UpdateOutcome {
    UpToDate,
    Available,
    Failed,
    InstallStarted,
    InstallFailed,
}
