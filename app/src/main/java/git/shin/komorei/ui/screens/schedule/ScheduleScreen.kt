package git.shin.komorei.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.SearchResultSkeleton
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.SurfaceVariantDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary

/** Monday-first localized weekday labels (index = [ScheduleDay.dayIndex]). */
private val scheduleDayLabels = intArrayOf(
    R.string.schedule_day_mon,
    R.string.schedule_day_tue,
    R.string.schedule_day_wed,
    R.string.schedule_day_thu,
    R.string.schedule_day_fri,
    R.string.schedule_day_sat,
    R.string.schedule_day_sun,
)

@Composable
fun ScheduleScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .testTag("schedule_screen")
    ) {
        Text(
            text = stringResource(R.string.schedule_title),
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.schedule_subtitle),
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        ScheduleDayStrip(
            days = state.days,
            selectedDayIndex = state.selectedDayIndex,
            onSelect = viewModel::selectDay,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoading) {
            SearchResultSkeleton()
        } else {
            val day = state.days.getOrNull(state.selectedDayIndex)
            if (day == null || day.entries.isEmpty()) {
                ScheduleEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 114.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = day.entries,
                        key = { "${it.anime.sourceId}_${it.anime.id}" },
                    ) { entry ->
                        ScheduleRow(
                            entry = entry,
                            sourceName = viewModel.getSourceName(entry.anime.sourceId),
                            onClick = { onAnimeClick(entry.anime) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayStrip(
    days: List<ScheduleDay>,
    selectedDayIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_day_strip"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(scheduleDayLabels.size) { index ->
            ScheduleDayChip(
                index = index,
                label = stringResource(scheduleDayLabels[index]),
                isToday = days.getOrNull(index)?.isToday == true,
                selected = index == selectedDayIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun ScheduleDayChip(
    index: Int,
    label: String,
    isToday: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AnimeRed else SurfaceVariantDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("schedule_day_$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextPrimary,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = if (isToday) stringResource(R.string.schedule_today) else " ",
            color = if (selected) Color.White.copy(alpha = 0.85f) else AnimeRed,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScheduleRow(
    entry: ScheduleEntry,
    sourceName: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("schedule_entry_${entry.anime.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.anime.posterUrl,
            contentDescription = entry.anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(64.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.anime.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sourceName,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = AnimeRed,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = entry.airTime,
                    color = AnimeRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.anime.currentEpisode
                        ?: stringResource(R.string.schedule_new_episode),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ScheduleEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.schedule_empty_title),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.schedule_empty_subtitle),
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
