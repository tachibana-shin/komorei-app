package git.shin.komorei.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceHomeData(
    val featured: List<Anime> = emptyList(),
    val sections: Map<String, List<Anime>> = emptyMap(),
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val repository: AnimeRepository = AnimeRepository()
) : ViewModel() {

    companion object {
        fun Factory(repository: AnimeRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(repository) as T
            }
        }
    }

    val sources: List<Source> = repository.sources

    private val _sourceDataMap = MutableStateFlow<Map<String, SourceHomeData>>(emptyMap())
    val sourceDataMap: StateFlow<Map<String, SourceHomeData>> = _sourceDataMap.asStateFlow()

    init {
        // Pre-fetch data for all sources so swiping horizontally between source tabs is fast & instant
        sources.forEach { source ->
            loadSourceData(source.id)
        }
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
