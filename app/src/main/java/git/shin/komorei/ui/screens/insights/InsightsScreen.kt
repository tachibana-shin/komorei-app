package git.shin.komorei.ui.screens.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import git.shin.komorei.R
import git.shin.komorei.data.Heatmap
import git.shin.komorei.data.HeatmapMonthHeader
import git.shin.komorei.data.InsightsData
import git.shin.komorei.data.SmallStat
import git.shin.komorei.data.YearlyMonth
import git.shin.komorei.ui.components.shimmerEffect
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.SurfaceVariantDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus

/**
 * "Thống kê" — the Compose counterpart of Aidoku's `InsightsView`.
 *
 * It keeps Aidoku's streaks, one-year activity heatmap and three-stat grid,
 * with a small yearly bar chart underneath. Anime-specific data is derived
 * from the local `watch_history` table by [InsightsViewModel].
 */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .testTag("insights_screen"),
    ) {
        ScreenHeader(onBack = onBack, title = stringResource(R.string.insights_title))

        when (val insights = data) {
            null -> InsightsLoading()
            else -> InsightsContent(insights)
        }
    }
}

@Composable
private fun ScreenHeader(
    onBack: () -> Unit,
    title: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .tvFocus(shape = CircleShape, scale = 1.15f)
                    .testTag("insights_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = TextPrimary,
            )
        }
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun InsightsLoading() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlatterShimmer()
        PlatterShimmer(height = 132.dp)
        PlatterShimmer(height = 96.dp)
    }
}

@Composable
private fun InsightsContent(data: InsightsData) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionLabel(stringResource(R.string.insights_streaks))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreakPlatter(
                label = stringResource(R.string.insights_current_streak),
                days = data.currentStreak,
                modifier = Modifier.weight(1f),
                testTag = "insights_current_streak",
            )
            if (data.longestStreak > data.currentStreak && data.longestStreak > 1) {
                StreakPlatter(
                    label = stringResource(R.string.insights_longest_streak),
                    days = data.longestStreak,
                    modifier = Modifier.weight(1f),
                    testTag = "insights_longest_streak",
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .padding(vertical = 12.dp),
        ) {
            HeatmapView(data.heatmap)
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.insights_stats))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            data.stats.forEach { stat ->
                StatTile(
                    stat = stat,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = stringResource(R.string.insights_time_note),
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )

        if (data.years.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.insights_activity))
            data.years.forEach { year ->
                YearCard(year)
            }
        }

        if (data.stats.all { it.total == 0 }) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.insights_empty),
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("insights_empty"),
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** Aidoku's `InsightPlatterView`: a rounded card holding one headline number. */
@Composable
private fun StreakPlatter(
    label: String,
    days: Int,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    Box(
        modifier =
            modifier
                .height(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .padding(12.dp)
                .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        if (days > 1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = days.toString(),
                    color = TextPrimary,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.insights_days),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.insights_no_streak),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.insights_no_streak_text),
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Aidoku's `HeatmapView`: one column per week and one row per weekday. The
 * horizontal scroll is deliberately local to this card; the page itself only
 * has the outer vertical scroll.
 */
@Composable
private fun HeatmapView(
    data: Heatmap,
    cellSize: Int = 12,
    spacing: Int = 3,
) {
    if (data.values.isEmpty()) return
    val step = cellSize + spacing
    val width = data.weeks * step
    val headers = remember(data) { InsightsData.heatmapMonthHeaders(data) }
    val buckets = remember(data.values) { HeatBuckets.from(data.values) }

    Column(
        modifier =
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
    ) {
        Row(modifier = Modifier.width(width.dp)) {
            var previousWeek = 0
            headers.forEach { header ->
                Spacer(Modifier.width(((header.week - previousWeek) * step).dp))
                MonthHeader(header)
                previousWeek = header.week + 1
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier =
                Modifier
                    .width(width.dp)
                    .height((7 * step).dp)
                    .testTag("insights_heatmap"),
        ) {
            data.values.forEachIndexed { index, value ->
                val week = index / 7
                val day = index % 7
                drawRoundRect(
                    color = buckets.colorFor(value),
                    topLeft = Offset((week * step).toFloat(), (day * step).toFloat()),
                    size = Size(cellSize.toFloat(), cellSize.toFloat()),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(header: HeatmapMonthHeader) {
    Text(
        text = InsightsData.monthLabel(header.month),
        color = TextMuted,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.width(15.dp),
    )
}

private data class HeatBuckets(
    val thresholds: IntArray,
    val cap: Int,
) {
    fun colorFor(value: Int): Color {
        if (value <= 0 || cap <= 0) return SurfaceVariantDark
        val clipped = value.coerceAtMost(cap)
        val bucket =
            thresholds
                .indexOfFirst { clipped <= it }
                .let { if (it < 0) thresholds.size else it }
        val alpha = HEAT_ALPHAS.getOrElse(bucket) { HEAT_ALPHAS.last() }
        return AnimeRed.copy(alpha = alpha)
    }

    companion object {
        private val HEAT_ALPHAS = floatArrayOf(0.30f, 0.40f, 0.55f, 0.75f, 1.0f)

        fun from(values: List<Int>): HeatBuckets {
            val nonZero = values.filter { it > 0 }.sorted()
            if (nonZero.isEmpty()) return HeatBuckets(IntArray(0), 0)
            val cap = percentile(nonZero, 0.95)
            val clipped = nonZero.map { it.coerceAtMost(cap) }.sorted()
            return HeatBuckets(
                thresholds =
                    intArrayOf(
                        percentile(clipped, 0.30),
                        percentile(clipped, 0.40),
                        percentile(clipped, 0.60),
                        percentile(clipped, 0.80),
                        percentile(clipped, 1.00),
                    ),
                cap = cap,
            )
        }

        private fun percentile(
            values: List<Int>,
            fraction: Double,
        ): Int {
            val index = ((values.size - 1) * fraction).toInt()
            return values[index.coerceIn(values.indices)]
        }
    }
}

@Composable
private fun StatTile(
    stat: SmallStat,
    modifier: Modifier = Modifier,
) {
    val title =
        when (stat.kind) {
            SmallStat.Kind.EPISODES -> stringResource(R.string.insights_stat_episodes)
            SmallStat.Kind.SERIES -> stringResource(R.string.insights_stat_series)
            SmallStat.Kind.HOURS -> stringResource(R.string.insights_stat_hours)
        }
    val unit =
        when (stat.kind) {
            SmallStat.Kind.EPISODES -> stringResource(R.string.insights_unit_episodes)
            SmallStat.Kind.SERIES -> stringResource(R.string.insights_unit_series)
            SmallStat.Kind.HOURS -> stringResource(R.string.insights_unit_hours)
        }

    Column(
        modifier =
            modifier
                .height(126.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .testTag("insights_stat_${stat.kind.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stat.total.toString(),
                color = TextPrimary,
                fontSize = 25.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = unit,
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        if (stat.thisYear > 0) {
            Text(
                text = stringResource(R.string.insights_this_year, stat.thisYear),
                color = AnimeRed,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (stat.thisMonth > 0) {
            Text(
                text = stringResource(R.string.insights_this_month, stat.thisMonth),
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One year as twelve compact bars, with its episode total. */
@Composable
private fun YearCard(year: YearlyMonth) {
    val max = year.months.maxOrNull() ?: 0
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .padding(14.dp)
                .testTag("insights_year_${year.year}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = year.year.toString(),
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.insights_year_total, year.total),
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            year.months.forEach { value ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SurfaceVariantDark),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (value > 0 && max > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height((value.toFloat() / max * 48f).dp.coerceAtLeast(3.dp))
                                    .background(AnimeRed),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(12) { index ->
                Text(
                    text = InsightsData.monthLabel(index).take(1),
                    color = TextMuted,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PlatterShimmer(height: Dp = 110.dp) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .shimmerEffect(),
    )
}
