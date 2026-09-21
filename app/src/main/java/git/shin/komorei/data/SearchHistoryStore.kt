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
 * Persists recent search queries as a history list.
 *
 * Stores up to [MAX_SIZE] recent queries in [SharedPreferences],
 * kept in reverse-chronological order (most recent first).
 * Each write updates the matching [StateFlow] so ViewModels react.
 */
@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    private companion object {
        const val MAX_SIZE = 20
        const val KEY_HISTORY = "recent_queries"
    }

    private val _history = MutableStateFlow<List<String>>(readHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /** Returns the history list (most recent first). */
    fun getHistory(): List<String> = _history.value

    /** Adds a query to the history. If already present, moves it to the top. */
    fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = _history.value.toMutableList()
        // Drop any existing entry that is a strict prefix of the new query —
        // e.g. typing "test" after a pause recorded "t"/"te"/"tes" already;
        // the settled query supersedes them so history stays clean.
        current.removeAll { it in query && it.length < query.length }
        // Remove if already present (will be re-added at top)
        current.remove(query)
        // Add to front
        current.add(0, query)
        // Trim to max size
        while (current.size > MAX_SIZE) {
            current.removeLast()
        }
        _history.value = current
        persist(current)
    }

    /** Clears all search history. */
    fun clearHistory() {
        _history.value = emptyList()
        persist(emptyList())
    }

    /** Removes a specific query from history. */
    fun removeQuery(query: String) {
        val current = _history.value.filter { it != query }.toMutableList()
        _history.value = current
        persist(current)
    }

    private fun readHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }
    }

    private fun persist(history: List<String>) {
        prefs.edit().putString(KEY_HISTORY, history.joinToString("\n")).apply()
    }
}
