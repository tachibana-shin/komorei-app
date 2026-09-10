package git.shin.komorei.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceHomeData(
    val featured: List<Anime> = emptyList(),
    val sections: Map<String, List<Anime>> = emptyMap(),
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
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
                map + (sourceId to existing.copy(isLoading = true))
            }
            val featured = repository.getFeaturedAnime(sourceId)
            val sections = repository.getSectionsForSource(sourceId)
            _sourceDataMap.update { map ->
                map + (sourceId to SourceHomeData(
                    featured = featured,
                    sections = sections,
                    isLoading = false
                ))
            }
        }
    }
}
