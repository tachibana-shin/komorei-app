package git.shin.komorei.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.InsightsData
import git.shin.komorei.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Loads the local history data used by the "Thống kê" screen. */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {
    private val _data = MutableStateFlow<InsightsData?>(null)

    /** Null while the first database read is in progress. */
    val data: StateFlow<InsightsData?> = _data.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the watch history, for example after returning to the screen. */
    fun refresh() {
        viewModelScope.launch {
            _data.value = withContext(Dispatchers.IO) {
                libraryRepository.getInsights()
            }
        }
    }
}
