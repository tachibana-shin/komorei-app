package git.shin.komorei.ui.screens.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import git.shin.komorei.R
import git.shin.komorei.data.LogEntry
import git.shin.komorei.data.LogLevel
import git.shin.komorei.data.LogStore
import git.shin.komorei.ui.theme.AnimeBlue
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus
import java.io.File

/**
 * The app's "Ghi nhật ký máy chủ" screen — an Aidoku-style log viewer.
 *
 * Aidoku shows the same information in `LogViewController`: a monospace,
 * level-coloured dump of everything the app and its sources printed, with a
 * Clear action and an export. Here it is a full Compose screen reached from
 * Settings, extended with the bits a phone UI can do better — live level
 * filtering, a "follow tail" auto-scroll, copy, and share-through-Android.
 *
 * A source's own `println!` / `env::print` arrives through
 * `KrxHostImpl.logPrint` → [LogStore], so this doubles as the per-source
 * debugging view (each line is tagged with the source that emitted it).
 */
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val entries by LogStore.entries.collectAsState()

    // null = show every level (Aidoku's unfiltered dump).
    var levelFilter: LogLevel? by remember { mutableStateOf(null) }
    var followTail by remember { mutableStateOf(true) }
    var shareError by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val visible =
        remember(entries, levelFilter) {
            filterEntries(entries, levelFilter)
        }

    // Aidoku's view streams new entries in live; follow the tail while the
    // user has not scrolled away from the bottom.
    LaunchedEffect(visible.size, followTail) {
        if (followTail && visible.isNotEmpty()) {
            listState.scrollToItem(visible.lastIndex)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding()
                .testTag("logs_screen"),
    ) {
        // ── Top bar: back / title / share + clear ───────────────────────────
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
                        .testTag("logs_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = stringResource(R.string.logs_title),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.logs_subtitle, entries.size),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = { shareError = !shareLog(context) },
                modifier =
                    Modifier
                        .tvFocus(shape = CircleShape, scale = 1.15f)
                        .testTag("logs_share"),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.logs_share_cd),
                    tint = TextPrimary,
                )
            }
            IconButton(
                onClick = { LogStore.clear() },
                modifier =
                    Modifier
                        .tvFocus(shape = CircleShape, scale = 1.15f)
                        .testTag("logs_clear"),
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.logs_clear),
                    tint = AnimeRed,
                )
            }
        }

        if (shareError) {
            Text(
                text = stringResource(R.string.logs_share_failed),
                color = AnimeRed,
                fontSize = 12.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AnimeRed.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("logs_share_error"),
            )
        }

        // ── Level filter chips (Aidoku colours its badges; here they filter) ─
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogLevelChip(
                text = stringResource(R.string.logs_level_all),
                selected = levelFilter == null,
                accent = TextPrimary,
                onClick = { levelFilter = null },
                testTag = "logs_filter_all",
            )
            listOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR).forEach { level ->
                LogLevelChip(
                    text = level.label,
                    selected = levelFilter == level,
                    accent = levelColor(level),
                    onClick = { levelFilter = if (levelFilter == level) null else level },
                    testTag = "logs_filter_${level.name.lowercase()}",
                )
            }
        }

        // ── Log body ────────────────────────────────────────────────────────
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        stringResource(
                            if (entries.isEmpty()) R.string.logs_empty else R.string.logs_empty_filtered,
                        ),
                    color = TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.testTag("logs_empty"),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("logs_list"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(visible, key = { it.timestamp to it.message.hashCode() }) { entry ->
                    LogRow(entry = entry)
                }
            }
        }

        // ── Follow-tail toggle ──────────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(CardDark)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.logs_visible_count, visible.size),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { followTail = !followTail },
                modifier =
                    Modifier
                        .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)
                        .testTag("logs_follow"),
            ) {
                Text(
                    text =
                        stringResource(
                            if (followTail) R.string.logs_following else R.string.logs_follow,
                        ),
                    color = if (followTail) AnimeBlue else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** One log line: coloured level badge, optional source tag, monospace body. */
@Composable
private fun LogRow(
    entry: LogEntry,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CardDark.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = timeFormat(entry.timestamp),
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp,
        )
        if (entry.level != LogLevel.DEFAULT) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = entry.level.label,
                color = levelColor(entry.level),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                lineHeight = 15.sp,
            )
        }
        entry.sourceId?.let { source ->
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = source,
                color = AnimeBlue,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = entry.message,
            color = TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp,
        )
    }
}

/** Small selectable chip used for the level filters. */
@Composable
private fun LogLevelChip(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
) {
    val shape = RoundedCornerShape(100)
    Text(
        text = text,
        color = if (selected) BackgroundDark else accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
        modifier =
            Modifier
                // TV focus highlight (no-op on phones) — a small chip scales cleanly.
                .tvFocus(shape = shape, scale = 1.08f)
                .clip(shape)
                .background(if (selected) accent else accent.copy(alpha = 0.14f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag(testTag),
    )
}

/** Aidoku's badge palette: blue info, yellow warn, red error, neutral debug. */
@Composable
private fun levelColor(level: LogLevel): Color =
    when (level) {
        LogLevel.DEFAULT -> TextSecondary
        LogLevel.DEBUG -> TextSecondary
        LogLevel.INFO -> AnimeBlue
        LogLevel.WARN -> Color(0xFFFFC107)
        LogLevel.ERROR -> AnimeRed
    }

private fun filterEntries(
    entries: List<LogEntry>,
    level: LogLevel?,
): List<LogEntry> = if (level == null) entries else entries.filter { it.level == level }

private fun timeFormat(timestamp: Long): String =
    java.text
        .SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        .format(java.util.Date(timestamp))

/**
 * Writes the buffer to a cache file and fires an ACTION_SEND chooser.
 * Returns false when the write or the chooser could not start.
 */
private fun shareLog(context: android.content.Context): Boolean =
    runCatching {
        val file =
            LogStore.exportTo(File(context.cacheDir, "logs"))
                ?: return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.logs_share)))
        true
    }.getOrDefault(false)
