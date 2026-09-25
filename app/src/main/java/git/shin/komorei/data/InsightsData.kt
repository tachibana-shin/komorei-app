package git.shin.komorei.data

import git.shin.komorei.data.local.entity.WatchHistoryEntity
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One of Aidoku's `SmallStatData` tiles. */
data class SmallStat(
    val total: Int,
    val thisMonth: Int,
    val thisYear: Int,
    /** Stable key for the tile; the screen resolves the localized labels. */
    val kind: Kind,
) {
    enum class Kind { EPISODES, SERIES, HOURS }
}

/** Per-month counts for one calendar year (Aidoku's `YearlyMonthData`). */
data class YearlyMonth(
    val year: Int,
    val months: List<Int>,
) {
    val total: Int get() = months.sum()
}

/** Aidoku's `HeatmapData`: one value per day, aligned to week boundaries. */
data class Heatmap(
    val startDayStartOfDay: Long,
    val values: List<Int>,
) {
    val weeks: Int get() = (values.size + 6) / 7
}

/** A month label positioned at the first week column where that month starts. */
data class HeatmapMonthHeader(
    val week: Int,
    val month: Int,
)

/**
 * The "Thống kê" dataset — the Android counterpart of Aidoku's
 * `Core/Library/Models/Insights/InsightsData`.
 *
 * The vocabulary maps directly: pages → episodes, series → anime, and
 * reading sessions → the app's saved `watch_history` rows. The latter stores
 * the last position for an episode rather than a session log, so the numbers
 * describe distinct episodes with saved progress; that is also what the
 * resume feature reports.
 */
data class InsightsData(
    val currentStreak: Int,
    val longestStreak: Int,
    val heatmap: Heatmap,
    val years: List<YearlyMonth>,
    val stats: List<SmallStat>,
) {
    companion object {
        private const val HOUR_MS = 60L * 60 * 1000

        /**
         * Builds the dataset from the watch history rows.
         *
         * [now] and [timeZone] are injectable so midnight/DST boundaries are
         * deterministic in tests. Rows dated in the future are ignored; this
         * can happen briefly when a device clock is corrected.
         */
        fun from(
            history: List<WatchHistoryEntity>,
            now: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault(),
        ): InsightsData {
            val today = startOfDay(now, timeZone)
            val rows = history.filter { it.lastWatchedAt <= now }

            val byDay = HashMap<Long, Int>()
            val byMonth = HashMap<Pair<Int, Int>, Int>()
            val seriesByMonth = HashMap<Pair<Int, Int>, MutableSet<Pair<String, String>>>()
            val seriesByYear = HashMap<Int, MutableSet<Pair<String, String>>>()
            val hoursByMonth = HashMap<Pair<Int, Int>, Long>()
            val hoursByYear = HashMap<Int, Long>()
            var totalHoursMs = 0L

            for (row in rows) {
                val day = startOfDay(row.lastWatchedAt, timeZone)
                byDay[day] = (byDay[day] ?: 0) + 1

                val calendar = calendar(timeZone).apply { timeInMillis = day }
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val monthKey = year to month
                byMonth[monthKey] = (byMonth[monthKey] ?: 0) + 1

                val seriesKey = row.animeId to row.sourceId
                seriesByMonth.getOrPut(monthKey) { HashSet() }.add(seriesKey)
                seriesByYear.getOrPut(year) { HashSet() }.add(seriesKey)

                // A history row is the last known position, not an accumulated
                // session. It is the closest Android equivalent to Aidoku's
                // hours-total, and is what auto-resume uses as well.
                val progress = row.progressMs.coerceAtLeast(0L)
                totalHoursMs += progress
                hoursByMonth[monthKey] = (hoursByMonth[monthKey] ?: 0L) + progress
                hoursByYear[year] = (hoursByYear[year] ?: 0L) + progress
            }

            val range = heatmapRange(today, timeZone)
            val heatmapValues = ArrayList<Int>(range.totalDays)
            var cursor = range.startDay
            repeat(range.totalDays) {
                heatmapValues += byDay[cursor] ?: 0
                cursor = addDays(cursor, 1, timeZone)
            }

            val (currentStreak, longestStreak) = streakLengths(byDay.keys, today, timeZone)
            val years =
                byMonth.keys
                    .map { it.first }
                    .distinct()
                    .sortedDescending()
                    .map { year ->
                        YearlyMonth(year, (1..12).map { month -> byMonth[year to month] ?: 0 })
                    }

            val nowCalendar = calendar(timeZone).apply { timeInMillis = now }
            val currentYear = nowCalendar.get(Calendar.YEAR)
            val currentMonth = currentYear to (nowCalendar.get(Calendar.MONTH) + 1)
            val allSeries = rows.map { it.animeId to it.sourceId }.toSet()

            return InsightsData(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                heatmap = Heatmap(range.startDay, heatmapValues),
                years = years,
                stats =
                    listOf(
                        SmallStat(
                            total = rows.size,
                            thisMonth = byMonth[currentMonth] ?: 0,
                            thisYear = rows.count { yearOf(it.lastWatchedAt, timeZone) == currentYear },
                            kind = SmallStat.Kind.EPISODES,
                        ),
                        SmallStat(
                            total = allSeries.size,
                            thisMonth = seriesByMonth[currentMonth]?.size ?: 0,
                            thisYear = seriesByYear[currentYear]?.size ?: 0,
                            kind = SmallStat.Kind.SERIES,
                        ),
                        SmallStat(
                            total = (totalHoursMs / HOUR_MS).toInt(),
                            thisMonth = ((hoursByMonth[currentMonth] ?: 0L) / HOUR_MS).toInt(),
                            thisYear = ((hoursByYear[currentYear] ?: 0L) / HOUR_MS).toInt(),
                            kind = SmallStat.Kind.HOURS,
                        ),
                    ),
            )
        }

        /** Month labels for the heatmap header, in calendar order. */
        fun heatmapMonthHeaders(
            heatmap: Heatmap,
            timeZone: TimeZone = TimeZone.getDefault(),
        ): List<HeatmapMonthHeader> {
            if (heatmap.values.isEmpty()) return emptyList()
            val result = mutableListOf<HeatmapMonthHeader>()
            var previousMonth: Int? = null
            for (week in 0 until heatmap.weeks) {
                val day = addDays(heatmap.startDayStartOfDay, week * 7, timeZone)
                val month =
                    calendar(timeZone)
                        .apply { timeInMillis = day }
                        .get(Calendar.MONTH)
                if (month != previousMonth) {
                    // Adjacent one-week month labels are too cramped to read;
                    // Aidoku drops the earlier label in the same situation.
                    if (result.isNotEmpty() && week == result.last().week + 1) {
                        result.removeAt(result.lastIndex)
                    }
                    result += HeatmapMonthHeader(week, month)
                    previousMonth = month
                }
            }
            return result
        }

        /** Short month label used by the heatmap and yearly chart. */
        fun monthLabel(monthIndex: Int): String {
            val symbols = DateFormatSymbols(Locale.getDefault()).shortMonths
            val label =
                symbols
                    .getOrNull(monthIndex.coerceIn(0, 11))
                    ?.takeIf { it.isNotBlank() }
                    .orEmpty()
            return label.take(1)
        }

        private data class HeatmapRange(
            val startDay: Long,
            val totalDays: Int,
        )

        private fun heatmapRange(
            today: Long,
            timeZone: TimeZone,
        ): HeatmapRange {
            val earliest = addDays(today, -364, timeZone)
            val calendar = calendar(timeZone).apply { timeInMillis = earliest }
            val back = (calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + 7) % 7
            val start = addDays(earliest, -back, timeZone)
            return HeatmapRange(start, 365 + back)
        }

        private fun streakLengths(
            activeDays: Set<Long>,
            today: Long,
            timeZone: TimeZone,
        ): Pair<Int, Int> {
            val days = activeDays.filter { it <= today }.sorted()
            // Aidoku requires two days before showing a streak.
            if (days.size < 2) return 0 to 0

            var longest = 1
            var run = 1
            for (index in 1 until days.size) {
                run = if (addDays(days[index - 1], 1, timeZone) == days[index]) run + 1 else 1
                if (run > longest) longest = run
            }

            val yesterday = addDays(today, -1, timeZone)
            val current = if (days.last() == today || days.last() == yesterday) run else 0
            return if (longest >= 2) current to longest else 0 to 0
        }

        private fun calendar(timeZone: TimeZone): Calendar = Calendar.getInstance(timeZone).apply { isLenient = false }

        private fun startOfDay(
            timestamp: Long,
            timeZone: TimeZone,
        ): Long =
            calendar(timeZone)
                .apply {
                    timeInMillis = timestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

        private fun yearOf(
            timestamp: Long,
            timeZone: TimeZone,
        ): Int = calendar(timeZone).apply { timeInMillis = timestamp }.get(Calendar.YEAR)

        private fun addDays(
            timestamp: Long,
            days: Int,
            timeZone: TimeZone,
        ): Long =
            calendar(timeZone)
                .apply {
                    timeInMillis = timestamp
                    add(Calendar.DAY_OF_YEAR, days)
                }.timeInMillis
    }
}
