package git.shin.komorei

import git.shin.komorei.data.LogEntry
import git.shin.komorei.data.LogLevel
import git.shin.komorei.data.LogStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Unit tests for [LogStore] — the app's counterpart to Aidoku's `LogStore`
 * actor, and the backing store for the "Ghi nhật ký máy chủ" screen.
 *
 * `LogStore` is a process-wide singleton, so each test starts from a cleared
 * buffer (and leaves it cleared) rather than asserting on shared state.
 */
class LogStoreTest {
    @Before
    fun setUp() = LogStore.clear()

    @After
    fun tearDown() = LogStore.clear()

    @Test
    fun entriesAreExposedInInsertionOrder() {
        LogStore.info("first")
        LogStore.warn("second")

        assertEquals(listOf("first", "second"), LogStore.entries.value.map { it.message })
    }

    @Test
    fun levelAndSourceAreRecorded() {
        LogStore.add(LogLevel.DEFAULT, "println from a source", "vi.fake-source")

        val entry = LogStore.entries.value.single()
        assertEquals(LogLevel.DEFAULT, entry.level)
        assertEquals("vi.fake-source", entry.sourceId)
        assertEquals("println from a source", entry.message)
    }

    @Test
    fun bufferIsBoundedAndDropsOldestFirst() {
        repeat(LogStore.MAX_ENTRIES + 50) { LogStore.info("line $it") }

        val entries = LogStore.entries.value
        assertEquals(LogStore.MAX_ENTRIES, entries.size)
        // The oldest 50 were evicted, so the head is the 51st line.
        assertEquals("line 50", entries.first().message)
        assertEquals("line ${LogStore.MAX_ENTRIES + 49}", entries.last().message)
    }

    @Test
    fun clearEmptiesTheBuffer() {
        LogStore.error("boom")
        LogStore.clear()

        assertTrue(LogStore.entries.value.isEmpty())
    }

    @Test
    fun formattedMatchesAidokuLayout() {
        val entry = LogEntry(timestamp = 0L, level = LogLevel.ERROR, message = "bang")

        // `[MM/dd HH:mm:ss.SSS] [LEVEL] message`
        val text = entry.formatted()
        assertTrue(text, text.matches(Regex("""\[\d{2}/\d{2} \d{2}:\d{2}:\d{2}\.\d{3}] \[ERROR] bang""")))
    }

    @Test
    fun defaultLevelHasNoBadge() {
        val text = LogEntry(timestamp = 0L, level = LogLevel.DEFAULT, message = "plain").formatted()

        assertTrue(text, !text.contains("[DEFAULT]"))
        assertTrue(text.endsWith("] plain"))
    }

    @Test
    fun exportJoinsEveryLine() {
        LogStore.info("a")
        LogStore.error("b")

        val lines = LogStore.export().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("[INFO] a"))
        assertTrue(lines[1].endsWith("[ERROR] b"))
    }

    @Test
    fun exportToWritesTimestampedFile() {
        LogStore.info("payload")

        val dir = File(System.getProperty("java.io.tmpdir"), "komorei-logstore-test")
        val file = LogStore.exportTo(dir)

        assertTrue(file != null && file.exists())
        val name = file!!.name
        assertTrue(name, name.matches(Regex("""log_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}\.txt""")))
        assertTrue(file.readText().endsWith("payload"))

        file.delete()
        dir.delete()
    }

    @Test
    fun exportToReturnsNullWhenTheDirectoryCannotBeCreated() {
        // A path whose parent is a FILE cannot become a directory.
        val blocker = File(System.getProperty("java.io.tmpdir"), "komorei-logstore-blocker")
        blocker.writeText("not a directory")
        val dir = File(blocker, "logs")

        assertEquals(null, LogStore.exportTo(dir))

        blocker.delete()
    }

    @Test
    fun exportedTextUsesTheDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            // A locale with a non-Gregorian calendar would otherwise render a
            // completely different date format in the exported log.
            Locale.setDefault(Locale("th", "TH"))
            val text = LogEntry(timestamp = 0L, level = LogLevel.INFO, message = "x").formatted()
            assertTrue(text, text.matches(Regex("""\[01/01 \d{2}:\d{2}:\d{2}\.\d{3}] \[INFO] x""")))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
