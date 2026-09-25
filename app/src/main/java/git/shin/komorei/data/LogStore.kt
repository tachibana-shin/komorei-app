package git.shin.komorei.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Severity of a log line (Aidoku's `LogType`).
 *
 * [DEFAULT] carries no badge — it is a bare source `println!` (see Aidoku's
 * `printHandler`), while the others are app- or runner-level events.
 */
enum class LogLevel(
    val label: String,
) {
    DEFAULT(""),
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
}

/** One line in the log. [sourceId] is set for anything a source itself emitted. */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.DEFAULT,
    val message: String,
    val sourceId: String? = null,
) {
    /** `[MM/dd HH:mm:ss.SSS] [LEVEL] message`, matching Aidoku's `LogEntry.formatted()`. */
    fun formatted(): String {
        val time = SimpleDateFormat("MM/dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
        val badge = if (level == LogLevel.DEFAULT) "" else "[${level.label}] "
        return "[$time] $badge$message"
    }
}

/**
 * Process-wide in-memory ring of log lines, the app's counterpart to Aidoku's
 * `LogStore` actor.
 *
 * A source's own `println!` / `env::print` reaches the app through
 * `KrxHostImpl.logPrint` and lands here — that is the "server log" the Logs
 * screen shows. Kept deliberately cheap and synchronous (the caller is the
 * wasm runner thread and must not block on a dispatcher hop), and bounded by
 * [MAX_ENTRIES] so a chatty source cannot grow it without limit.
 */
object LogStore {
    /** Older lines are dropped once the buffer is full. */
    const val MAX_ENTRIES = 2000

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val lock = Any()

    /** Appends a line, evicting the oldest ones past [MAX_ENTRIES]. */
    fun add(
        level: LogLevel = LogLevel.DEFAULT,
        message: String,
        sourceId: String? = null,
    ) {
        val entry = LogEntry(level = level, message = message, sourceId = sourceId)
        synchronized(lock) {
            val current = _entries.value
            val next =
                if (current.size >= MAX_ENTRIES) {
                    current.subList(current.size - MAX_ENTRIES + 1, current.size).toList() + entry
                } else {
                    current + entry
                }
            _entries.value = next
        }
        LogStreamClient.send(entry.formatted())
    }

    fun debug(
        message: String,
        sourceId: String? = null,
    ) = add(LogLevel.DEBUG, message, sourceId)

    fun info(
        message: String,
        sourceId: String? = null,
    ) = add(LogLevel.INFO, message, sourceId)

    fun warn(
        message: String,
        sourceId: String? = null,
    ) = add(LogLevel.WARN, message, sourceId)

    fun error(
        message: String,
        sourceId: String? = null,
    ) = add(LogLevel.ERROR, message, sourceId)

    fun clear() = synchronized(lock) { _entries.value = emptyList() }

    /** The whole buffer as one newline-joined block (AIDOKU's export payload). */
    fun export(): String = _entries.value.joinToString("\n") { it.formatted() }

    /**
     * Writes [export] to [dir] as `log_<yyyy-MM-dd_HH-mm-ss>.txt` (AIDOKU's
     * naming) and returns the file, or null if the write failed.
     */
    fun exportTo(dir: File): File? =
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "log_$stamp.txt")
            file.writeText(export())
            file
        }.getOrNull()
}
