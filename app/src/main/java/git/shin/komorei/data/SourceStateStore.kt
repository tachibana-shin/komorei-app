package git.shin.komorei.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted per-source state — the Android analogue of Aidoku's
 * `Browse.disabledSources` / `Browse.pinned` UserDefaults.
 *
 * Everything is a plain [SharedPreferences] StringSet / newline-joined string so
 * it survives process death and works on JVM tests (Robolectric) without extra
 * wiring. Each write updates the matching [StateFlow] so ViewModels react.
 */
@Singleton
class SourceStateStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("source_manager", Context.MODE_PRIVATE)

    // ── disabled sources ────────────────────────────────────────────────────

    private val _disabled = MutableStateFlow(readDisabled())
    val disabled: StateFlow<Set<String>> = _disabled.asStateFlow()

    private fun readDisabled(): Set<String> =
        prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty().toSet()

    fun isDisabled(sourceId: String): Boolean = sourceId in _disabled.value

    fun setDisabled(sourceId: String, disabled: Boolean) {
        val next = _disabled.value.toMutableSet()
        if (disabled) next += sourceId else next -= sourceId
        prefs.edit().putStringSet(KEY_DISABLED, next).apply()
        _disabled.value = next
    }

    // ── pinned sources (ordered) ────────────────────────────────────────────

    private val _pinned = MutableStateFlow(readPinned())
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private fun readPinned(): List<String> =
        prefs.getString(KEY_PINNED, null)?.split('\n').orEmpty().filter { it.isNotBlank() }

    fun pinnedIndex(sourceId: String): Int = _pinned.value.indexOf(sourceId)

    /** Pins to the end of the list; unpins when already pinned. */
    fun togglePinned(sourceId: String) {
        val current = readPinned().toMutableList()
        if (sourceId in current) {
            current.remove(sourceId)
        } else {
            current += sourceId
        }
        prefs.edit().putString(KEY_PINNED, current.joinToString("\n")).apply()
        _pinned.value = current
    }

    fun unpin(sourceId: String) {
        val current = readPinned().toMutableList()
        if (current.remove(sourceId)) {
            prefs.edit().putString(KEY_PINNED, current.joinToString("\n")).apply()
            _pinned.value = current
        }
    }

    // ── repos (ordered) ─────────────────────────────────────────────────────

    private val _repos = MutableStateFlow(readRepos())
    val repos: StateFlow<List<String>> = _repos.asStateFlow()

    private fun readRepos(): List<String> =
        prefs.getString(KEY_REPOS, null)?.split('\n').orEmpty().filter { it.isNotBlank() }

    fun addRepo(url: String): Boolean {
        val normalized = url.trim().trimEnd('/')
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return false
        if (normalized in _repos.value) return true // idempotent
        val next = _repos.value + normalized
        prefs.edit().putString(KEY_REPOS, next.joinToString("\n")).apply()
        _repos.value = next
        return true
    }

    fun removeRepo(url: String) {
        val next = _repos.value.toMutableList().apply { remove(url) }
        prefs.edit().putString(KEY_REPOS, next.joinToString("\n")).apply()
        _repos.value = next
    }

    /** A structured snapshot used by the backup repository. */
    fun snapshot(): SourceStateSnapshot = SourceStateSnapshot(
        disabledSources = disabled.value.sorted(),
        pinnedSources = pinned.value.toList(),
        repositoryUrls = repos.value.toList(),
    )

    /** Restores the state and publishes it to all existing collectors. */
    fun restore(snapshot: SourceStateSnapshot) {
        val validRepos = snapshot.repositoryUrls.mapNotNull(::normalizeRepoUrl).distinct()
        val nextDisabled = snapshot.disabledSources.toSet()
        val nextPinned = snapshot.pinnedSources.distinct()
        prefs.edit()
            .putStringSet(KEY_DISABLED, nextDisabled)
            .putString(KEY_PINNED, nextPinned.joinToString("\n"))
            .putString(KEY_REPOS, validRepos.joinToString("\n"))
            .apply()
        _disabled.value = nextDisabled
        _pinned.value = nextPinned
        _repos.value = validRepos
    }

    private fun normalizeRepoUrl(url: String): String? {
        val normalized = url.trim().trimEnd('/')
        return normalized.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private companion object {
        const val KEY_DISABLED = "disabled_sources"
        const val KEY_PINNED = "pinned_sources"
        const val KEY_REPOS = "source_repos"
    }
}

data class SourceStateSnapshot(
    val disabledSources: List<String>,
    val pinnedSources: List<String>,
    val repositoryUrls: List<String>,
)
