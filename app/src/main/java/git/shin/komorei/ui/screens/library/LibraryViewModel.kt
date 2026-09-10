package git.shin.komorei.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _allAnimes = repository.sources.flatMap { src ->
        repository.getFeaturedAnime(src.id) + repository.getSectionsForSource(src.id).values.flatten()
    }.distinctBy { it.id }

    private val _bookmarkedAnimeIds = MutableStateFlow<Set<String>>(
        setOf("solo_leveling_s2", "frieren_journey")
    )
    val bookmarkedAnimeIds: StateFlow<Set<String>> = _bookmarkedAnimeIds.asStateFlow()

    val bookmarkedAnimes: StateFlow<List<Anime>> = _bookmarkedAnimeIds
        .combine(MutableStateFlow(_allAnimes)) { ids, all ->
            all.filter { it.id in ids }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _allAnimes.filter { it.id in _bookmarkedAnimeIds.value }
        )

    private val _historyAnimes = MutableStateFlow<List<Anime>>(
        listOfNotNull(
            _allAnimes.find { it.id == "solo_leveling_s2" },
            _allAnimes.find { it.id == "jujutsu_kaisen_s2" }
        )
    )
    val historyAnimes: StateFlow<List<Anime>> = _historyAnimes.asStateFlow()

    fun toggleBookmark(animeId: String) {
        _bookmarkedAnimeIds.update { set ->
            if (set.contains(animeId)) set - animeId else set + animeId
        }
    }

    fun addToHistory(anime: Anime) {
        _historyAnimes.update { current ->
            listOf(anime) + current.filter { it.id != anime.id }
        }
    }
}
