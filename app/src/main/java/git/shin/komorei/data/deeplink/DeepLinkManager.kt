package git.shin.komorei.data.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between Android intents and the UI: [MainActivity] submits the raw
 * incoming deep-link URL here (before composition exists), and the
 * [DeepLinkViewModel] consumes it once the UI is up. Last link wins.
 */
@Singleton
class DeepLinkManager @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)

    /** The latest incoming deep-link URL, or null once consumed. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Registers an incoming intent data URI (raw string, e.g. `komorei://…`). */
    fun submit(rawUrl: String) {
        _pending.value = rawUrl
    }

    /** Marks the link handled so it won't re-trigger (also clears the pending value). */
    fun markConsumed() {
        if (_pending.value != null) _pending.value = null
    }
}
