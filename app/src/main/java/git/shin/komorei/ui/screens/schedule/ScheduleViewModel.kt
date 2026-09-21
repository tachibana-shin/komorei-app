package git.shin.komorei.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.AnimeRepository
import git.shin.komorei.model.Anime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/** One anime airing on a given weekday. */
data class ScheduleEntry(
    val anime: Anime,
    val airTime: String,
)

/**
 * Every entry for a weekday. [dayIndex] is Monday-based (0 = Thứ 2 … 6 = Chủ nhật),
 * so the screen can resolve the localized label itself.
 */
data class ScheduleDay(
    val dayIndex: Int,
    val isToday: Boolean,
    val entries: List<ScheduleEntry>,
)

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val days: List<ScheduleDay> = emptyList(),
    val selectedDayIndex: Int = 0,
)

/**
 * Weekly airing schedule.
 *
 * All data is fake (like the rest of the app): the catalogue returned by
 * [AnimeRepository.allAnimes] is distributed deterministically across the seven
 * weekdays, with stable evening time slots. Swapping in a real schedule source
 * later only means replacing [load]'s body.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: AnimeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val animes = runCatching { repository.allAnimes() }.getOrDefault(emptyList())
            val buckets = List(DAYS_PER_WEEK) { mutableListOf<Anime>() }
            animes.forEachIndexed { index, anime ->
                buckets[index % DAYS_PER_WEEK].add(anime)
            }

            val todayIndex = mondayBasedTodayIndex()
            val days = buckets.mapIndexed { index, list ->
                ScheduleDay(
                    dayIndex = index,
                    isToday = index == todayIndex,
                    entries = list
                        .sortedBy { it.title.lowercase(Locale.getDefault()) }
                        .mapIndexed { position, anime ->
                            ScheduleEntry(anime = anime, airTime = airTimeFor(position))
                        },
                )
            }

            _uiState.value = ScheduleUiState(
                isLoading = false,
                days = days,
                selectedDayIndex = todayIndex,
            )
        }
    }

    fun selectDay(index: Int) {
        _uiState.value = _uiState.value.copy(selectedDayIndex = index)
    }

    /** Source display name for a card's badge (never the raw source id). */
    fun getSourceName(sourceId: String): String = repository.getSourceName(sourceId)

    /** Evening slots 18:00–21:00, stable across reloads. */
    private fun airTimeFor(position: Int): String =
        String.format(Locale.US, "%02d:00", 18 + (position % 4))

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}

/**
 * Monday = 0 … Sunday = 6, matching [ScheduleDay.dayIndex].
 *
 * Pure so it can be unit-tested without a [Calendar] and without the wasm runner.
 */
internal fun mondayBasedDayIndex(calendarDayOfWeek: Int): Int =
    (calendarDayOfWeek + 5) % 7

private fun mondayBasedTodayIndex(): Int =
    mondayBasedDayIndex(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
