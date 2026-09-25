package git.shin.komorei

import git.shin.komorei.data.InsightsData
import git.shin.komorei.data.SmallStat
import git.shin.komorei.data.local.entity.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Pure aggregation tests for the Aidoku-style Insights data. */
class InsightsDataTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun emptyHistoryHasNoStatsButKeepsAYearOfHeatmapCells() {
        val data =
            InsightsData.from(
                history = emptyList(),
                now = timestamp(2026, Calendar.JANUARY, 15),
                timeZone = utc,
            )

        assertEquals(0, data.currentStreak)
        assertEquals(0, data.longestStreak)
        assertTrue(data.heatmap.values.size in 365..371)
        assertTrue(data.heatmap.values.all { it == 0 })
        assertTrue(data.years.isEmpty())
        assertTrue(data.stats.all { it.total == 0 })
    }

    @Test
    fun aggregatesEpisodesDistinctAnimeAndSavedWatchTime() {
        val now = timestamp(2026, Calendar.JANUARY, 15)
        val data =
            InsightsData.from(
                history =
                    listOf(
                        history("a-1", "anime-a", now - 5 * DAY, 2 * HOUR),
                        history("a-2", "anime-a", now - 4 * DAY, HOUR),
                        history("b-1", "anime-b", now - 3 * DAY, 90 * MINUTE),
                        history("c-1", "anime-c", timestamp(2025, Calendar.DECEMBER, 20), 30 * MINUTE),
                    ),
                now = now,
                timeZone = utc,
            )

        assertEquals(4, data.stats.forStat(SmallStat.Kind.EPISODES).total)
        assertEquals(3, data.stats.forStat(SmallStat.Kind.EPISODES).thisMonth)
        assertEquals(3, data.stats.forStat(SmallStat.Kind.EPISODES).thisYear)
        assertEquals(3, data.stats.forStat(SmallStat.Kind.SERIES).total)
        assertEquals(2, data.stats.forStat(SmallStat.Kind.SERIES).thisMonth)
        assertEquals(2, data.stats.forStat(SmallStat.Kind.SERIES).thisYear)
        assertEquals(5, data.stats.forStat(SmallStat.Kind.HOURS).total)
        assertEquals(4, data.stats.forStat(SmallStat.Kind.HOURS).thisMonth)
        assertEquals(4, data.stats.forStat(SmallStat.Kind.HOURS).thisYear)
        assertEquals(listOf(2026, 2025), data.years.map { it.year })
        assertEquals(3, data.years.first().months[0])
        assertEquals(1, data.years.last().months[11])
    }

    @Test
    fun currentStreakMustEndTodayOrYesterday() {
        val now = timestamp(2026, Calendar.FEBRUARY, 10)
        val history =
            listOf(
                history("e-1", "anime", timestamp(2026, Calendar.FEBRUARY, 6)),
                history("e-2", "anime", timestamp(2026, Calendar.FEBRUARY, 7)),
                history("e-3", "anime", timestamp(2026, Calendar.FEBRUARY, 8)),
                history("e-4", "anime", timestamp(2026, Calendar.FEBRUARY, 10)),
            )

        val data = InsightsData.from(history, now, utc)

        assertEquals(1, data.currentStreak)
        assertEquals(3, data.longestStreak)
    }

    @Test
    fun isolatedDaysDoNotCreateAOneDayStreak() {
        val now = timestamp(2026, Calendar.FEBRUARY, 10)
        val history =
            listOf(
                history("e-1", "anime", timestamp(2026, Calendar.FEBRUARY, 8)),
                history("e-2", "anime", timestamp(2026, Calendar.FEBRUARY, 10)),
            )

        val data = InsightsData.from(history, now, utc)

        assertEquals(0, data.currentStreak)
        assertEquals(0, data.longestStreak)
    }

    @Test
    fun yesterdayStillCountsAsCurrent() {
        val now = timestamp(2026, Calendar.MARCH, 10)
        val history =
            listOf(
                history("e-1", "anime", timestamp(2026, Calendar.MARCH, 8)),
                history("e-2", "anime", timestamp(2026, Calendar.MARCH, 9)),
            )

        val data = InsightsData.from(history, now, utc)

        assertEquals(2, data.currentStreak)
        assertEquals(2, data.longestStreak)
    }

    @Test
    fun heatmapCountsOneSavedEpisodePerDay() {
        val now = timestamp(2026, Calendar.APRIL, 10)
        val data =
            InsightsData.from(
                history =
                    listOf(
                        history("e-1", "anime", timestamp(2026, Calendar.APRIL, 9)),
                        history("e-2", "anime", timestamp(2026, Calendar.APRIL, 9), 5_000),
                        history("e-3", "anime", timestamp(2026, Calendar.APRIL, 10)),
                    ),
                now = now,
                timeZone = utc,
            )

        assertTrue(data.heatmap.values.any { it == 2 })
        assertTrue(data.heatmap.values.any { it == 1 })
        assertTrue(InsightsData.heatmapMonthHeaders(data.heatmap, utc).isNotEmpty())
    }

    private fun history(
        episodeId: String,
        animeId: String,
        watchedAt: Long,
        progressMs: Long = HOUR,
    ) = WatchHistoryEntity(
        animeId = animeId,
        sourceId = "source",
        episodeId = episodeId,
        episodeNumber = "1",
        episodeTitle = "",
        lastWatchedAt = watchedAt,
        progressMs = progressMs,
        durationMs = progressMs,
    )

    private fun timestamp(
        year: Int,
        month: Int,
        day: Int,
    ): Long =
        Calendar
            .getInstance(utc)
            .apply {
                clear()
                set(year, month, day, 12, 0, 0)
            }.timeInMillis

    private fun List<SmallStat>.forStat(kind: SmallStat.Kind): SmallStat = first { it.kind == kind }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
