package git.shin.komorei.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.HomeComponent
import git.shin.komorei.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home tab state for one source: the FULL [getHome] layout — every
 * [HomeComponent] row of the runner's `home()` (BigScroller / ImageScroller /
 * Scroller / AnimeEpisodeList / AnimeList / Filters / Links), in source order.
 */
data class SourceHomeData(
    val home: List<HomeComponent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: AnimeRepository
) : ViewModel() {

    val sources: List<Source> = repository.sources

    private val _sourceDataMap = MutableStateFlow<Map<String, SourceHomeData>>(emptyMap())
    val sourceDataMap: StateFlow<Map<String, SourceHomeData>> = _sourceDataMap.asStateFlow()

    init {
        // Pre-fetch data for all sources so swiping horizontally between source tabs is fast & instant
        sources.forEach { source ->
            loadSourceData(source.id)
        }
    }

    fun getSourceName(sourceId: String): String {
        return repository.getSourceName(sourceId)
    }

    fun loadSourceData(sourceId: String) {
        viewModelScope.launch {
            _sourceDataMap.update { map ->
                val existing = map[sourceId] ?: SourceHomeData()
                map + (sourceId to existing.copy(isLoading = true, error = null))
            }
            runCatching {
                val home = repository.getHome(sourceId)
                _sourceDataMap.update { map ->
                    map + (sourceId to SourceHomeData(
                        home = home,
                        isLoading = false,
                        error = null,
                    ))
                }
            }.onFailure { e ->
                _sourceDataMap.update { map ->
                    val existing = map[sourceId] ?: SourceHomeData()
                    map + (sourceId to existing.copy(
                        isLoading = false,
                        error = e.message ?: appContext.getString(R.string.error_load_data)
                    ))
                }
            }
        }
    }
}