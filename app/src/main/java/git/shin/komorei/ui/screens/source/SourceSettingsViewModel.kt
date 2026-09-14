package git.shin.komorei.ui.screens.source

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.data.local.KrxDefaultsStore
import git.shin.komorei.data.local.krxDefaultsKey
import git.shin.komorei.data.remote.clearCookiesForHost
import git.shin.komorei.model.Source
import git.shin.komorei.model.SourceSetting
import git.shin.komorei.model.SourceSettingValue
import git.shin.komorei.sdk.runner.HostDefaultValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the per-source settings screen (`Screen.SourceSettings`): the
 * source's dynamic settings (`get_settings`) plus the app-level actions
 * (clear the cached home layout).
 *
 * Setting values are persisted through [KrxDefaultsStore] — the SAME store the
 * source reads/writes via its `defaults_get`/`defaults_set` imports (SQLite in
 * production). Keys are written namespaced by source id (`{sourceId}.{key}`),
 * which is exactly how the source's scoped host reads them back. After each
 * write the settings list is reloaded so the source can react (e.g. a
 * `requires` condition may now evaluate differently).
 */
@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val defaultsStore: KrxDefaultsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sourceId: String = savedStateHandle.get<String>("sourceId").orEmpty()

    data class UiState(
        val source: Source? = null,
        /** null = not loaded yet; emptyList = the source exposes no settings. */
        val settings: List<SourceSetting>? = null,
        /** Currently selected source languages — the `{sourceId}.languages`
         *  default, read the same way the source does (Aidoku: the app injects
         *  a language picker that writes this key, sources read it back via
         *  `defaults_get("languages")`). Empty = the source's full set applies. */
        val selectedLanguages: List<String> = emptyList(),
        val isLoading: Boolean = true,
        val error: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState(source = repository.getSource(sourceId)))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /** (Re)fetches the source's dynamic settings from the runner, plus the
     *  current per-source language selection from the defaults store. */
    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = false) }
            val languages = currentLanguages()
            runCatching { repository.getSettings(sourceId) }
                .onSuccess { settings ->
                    _uiState.update {
                        it.copy(
                            settings = settings,
                            selectedLanguages = languages,
                            isLoading = false,
                            error = false,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, error = true) }
                }
        }
    }

    /** Reads the `{sourceId}.languages` default (Aidoku's app-injected key). */
    private suspend fun currentLanguages(): List<String> {
        val v = defaultsStore.get(krxDefaultsKey(sourceId, "languages"))
        return (v as? HostDefaultValue.StringArray)?.v1 ?: emptyList()
    }

    /**
     * Persists the language picker selection under `{sourceId}.languages` —
     * the exact key sources read via `defaults_get::<Vec<String>>("languages")`.
     * Also drops the cached home layout (Aidoku posts `refresh-content` after a
     * language change; clearing the cache makes the next fetch re-read).
     */
    fun setLanguages(selection: List<String>) {
        val ordered = selection
        viewModelScope.launch {
            defaultsStore.set(krxDefaultsKey(sourceId, "languages"), HostDefaultValue.StringArray(ordered))
            _uiState.update { it.copy(selectedLanguages = ordered) }
            repository.clearCachedHome(sourceId)
        }
    }

    /**
     * "Reset Settings" — deletes EVERY stored default for this source
     * (`{sourceId}.` prefix), including the language selection, then reloads so
     * the list shows the settings' own description defaults again. Mirrors
     * Aidoku's `SourceManager.removeSettings(from: sourceKey)`.
     */
    fun resetSettings() {
        viewModelScope.launch {
            defaultsStore.deleteAll(sourceId)
            repository.clearCachedHome(sourceId)
            loadSettings()
        }
    }

    // ── writes (optimistic + silent reload) ──────────────────────────────

    /** Toggle a boolean setting. */
    fun toggleSetting(key: String, currentValue: Boolean) {
        writeAndReload(key, HostDefaultValue.Bool(!currentValue)) { settings ->
            settings.map { s ->
                if (s.key == key && s.value is SourceSettingValue.Toggle) {
                    s.copy(value = (s.value as SourceSettingValue.Toggle).copy(default = !currentValue))
                } else s
            }
        }
    }

    /** Select a single value (Select / Picker / Segment). */
    fun selectSetting(key: String, value: String) {
        writeAndReload(key, HostDefaultValue.String(value)) { settings ->
            settings.map { s ->
                if (s.key == key) {
                    when (val v = s.value) {
                        is SourceSettingValue.Select -> s.copy(value = v.copy(default = value))
                        is SourceSettingValue.Picker -> s.copy(value = v.copy(default = value))
                        is SourceSettingValue.Segment -> {
                            val idx = v.options.indexOf(value).coerceAtLeast(0)
                            s.copy(value = v.copy(default = idx))
                        }
                        else -> s
                    }
                } else s
            }
        }
    }

    /** Select a segment by index. */
    fun selectSegment(key: String, index: Int) {
        writeAndReload(key, HostDefaultValue.Int(index)) { settings ->
            settings.map { s ->
                if (s.key == key && s.value is SourceSettingValue.Segment) {
                    s.copy(value = (s.value as SourceSettingValue.Segment).copy(default = index))
                } else s
            }
        }
    }

    /** Toggle a value in a multi-select set. */
    fun toggleMultiSelect(key: String, option: String, currentSelection: List<String>) {
        val updated = if (option in currentSelection) {
            currentSelection - option
        } else {
            currentSelection + option
        }
        writeAndReload(key, HostDefaultValue.StringArray(updated)) { settings ->
            settings.map { s ->
                if (s.key == key && s.value is SourceSettingValue.MultiSelect) {
                    s.copy(value = (s.value as SourceSettingValue.MultiSelect).copy(default = updated))
                } else s
            }
        }
    }

    /** Set a stepper value. */
    fun setStepper(key: String, value: Double) {
        writeAndReload(key, HostDefaultValue.Float(value.toFloat())) { settings ->
            settings.map { s ->
                if (s.key == key && s.value is SourceSettingValue.Stepper) {
                    s.copy(value = (s.value as SourceSettingValue.Stepper).copy(default = value))
                } else s
            }
        }
    }

    /** Set a text field value. */
    fun setText(key: String, value: String) {
        writeAndReload(key, HostDefaultValue.String(value)) { settings ->
            settings.map { s ->
                if (s.key == key && s.value is SourceSettingValue.Text) {
                    s.copy(value = (s.value as SourceSettingValue.Text).copy(default = value))
                } else s
            }
        }
    }

    /** Write the value, optimistically update the local list, then silently reload. */
    private fun writeAndReload(
        key: String,
        value: HostDefaultValue,
        optimisticUpdate: (List<SourceSetting>) -> List<SourceSetting>,
    ) {
        // If the changed setting declares a `notification`, forward it to the
        // source's handle_notification after persisting (Aidoku calls
        // source.handleNotification for every setting change that has one).
        val notification = _uiState.value.settings.orEmpty().findSetting(key)?.notification

        // Optimistic UI update first so the toggle/selection flips instantly.
        _uiState.update { state ->
            state.copy(settings = state.settings?.let(optimisticUpdate))
        }
        viewModelScope.launch {
            // Persist (namespaced by source id — the same row the source's
            // scoped host reads via defaults_get), then silent reload (after
            // the write so the source's next `get_settings` reads the new value).
            defaultsStore.set(krxDefaultsKey(sourceId, key), value)
            notification?.let { repository.handleNotification(sourceId, it) }
            runCatching { repository.getSettings(sourceId) }
                .onSuccess { settings ->
                    _uiState.update { it.copy(settings = settings) }
                }
                // On failure keep the optimistic value; the store is already written.
        }
    }

    /**
     * A one-shot action button — forwards its `notification` to the source's
     * `handle_notification` on tap (Aidoku: button clicks call handleNotification
     * with the button's notification value). Nothing happens when the button
     * declares no notification.
     */
    fun runSetting(setting: SourceSetting) {
        val notification = setting.notification ?: return
        viewModelScope.launch {
            repository.handleNotification(sourceId, notification)
        }
    }

    /** Finds a setting by [key] anywhere in the tree (groups/pages included). */
    private fun List<SourceSetting>.findSetting(key: String): SourceSetting? =
        firstNotNullOfOrNull { s ->
            if (s.key == key) s
            else when (val v = s.value) {
                is SourceSettingValue.Group -> v.items.findSetting(key)
                is SourceSettingValue.Page -> v.items.findSetting(key)
                else -> null
            }
        }

    /** Drops the cached home layout so the source reloads it on next open. */
    fun clearCachedHome() {
        repository.clearCachedHome(sourceId)
    }

    /**
     * Clears every cookie stored for this source's domain (Aidoku's "Clear
     * Source Cache" also removes cookies for the source's URLs). Scoped to the
     * source's own `baseUrl` host — other sites are untouched.
     */
    fun clearCookies() {
        val baseUrl = (uiState.value.source ?: repository.getSource(sourceId))?.baseUrl ?: return
        val host = Uri.parse(baseUrl).host ?: return
        clearCookiesForHost(host)
    }
}
