package git.shin.komorei.sdk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide coordinator for HTTP JS-challenge bypasses (Cloudflare
 * "Just a moment", the localized "Xác Minh An Toàn", captchas, ...).
 *
 * Two cooperating tiers, mirroring the reference app (`~/app`'s CloudflareManager):
 *
 *  1. Headless solve — [withHeadlessLock] serializes the per-source hidden
 *     WebView attempts so concurrent 403s never spawn one WebView each. Runs on
 *     the runner thread (see [KrxHostImpl.solveHeadlessOnce]).
 *  2. Visible browser — when the headless tier cannot clear the challenge
 *     (a real captcha needs a human), the host calls [requestUserBypass]; that
 *     publishes [pending] for the UI
 *     ([git.shin.komorei.ui.components.dialogs.ChallengeBypassDialog]) and
 *     blocks the caller until [completeBypass] / [cancelBypass] fires.
 *
 * Single-flight by design: only ONE bypass is active process-wide; a second 403
 * while one is pending shares the same deferred outcome instead of opening a
 * second dialog.
 */
object JsChallengeCoordinator {
    /** A bypass that is waiting for the user in the visible WebView dialog. */
    data class PendingChallenge(
        val url: String,
    )

    private val lock = Any()

    private val _pending = MutableStateFlow<PendingChallenge?>(null)

    /** Non-null while the visible-browser dialog should be shown. */
    val pending: StateFlow<PendingChallenge?> = _pending.asStateFlow()

    private var activeBypass: CompletableDeferred<Boolean>? = null

    private val headlessLock = Any()

    /**
     * Serializes headless challenge solves across all sources. Concurrent
     * requests queue up (one hidden WebView at a time) instead of each spawning
     * its own WebView.
     */
    fun <T> withHeadlessLock(block: () -> T): T = synchronized(headlessLock) { block() }

    /**
     * Asks the user to finish the challenge in the visible browser dialog.
     * Suspends until [completeBypass] or [cancelBypass] is invoked and returns
     * true only when the user actually completed the challenge.
     */
    suspend fun requestUserBypass(url: String): Boolean {
        val deferred =
            synchronized(lock) {
                // Reuse an in-flight bypass — a concurrent 403 while the dialog is
                // up shares the same outcome instead of opening a second dialog.
                activeBypass ?: CompletableDeferred<Boolean>().also {
                    activeBypass = it
                    _pending.value = PendingChallenge(url)
                }
            }
        return deferred.await()
    }

    /** The dialog finished the challenge — resume the awaiting request. */
    fun completeBypass() {
        synchronized(lock) {
            activeBypass?.complete(true)
            activeBypass = null
            _pending.value = null
        }
    }

    /** The user dismissed the dialog without solving — fail the request. */
    fun cancelBypass() {
        synchronized(lock) {
            activeBypass?.complete(false)
            activeBypass = null
            _pending.value = null
        }
    }

    /** Test seam: forget any in-flight bypass state. */
    internal fun resetForTest() {
        synchronized(lock) {
            activeBypass?.complete(false)
            activeBypass = null
            _pending.value = null
        }
    }
}
