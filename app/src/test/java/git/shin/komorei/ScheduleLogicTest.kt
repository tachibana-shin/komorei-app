package git.shin.komorei

import git.shin.komorei.ui.screens.schedule.mondayBasedDayIndex
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * The schedule tab is Monday-first; [Calendar] is Sunday-first. Locks the
 * conversion so "Hôm nay" always highlights the right weekday.
 */
class ScheduleLogicTest {

    @Test
    fun `Calendar sunday maps to index 6 (Chủ nhật)`() {
        assertEquals(6, mondayBasedDayIndex(Calendar.SUNDAY))
    }

    @Test
    fun `Calendar monday maps to index 0 (Thứ 2)`() {
        assertEquals(0, mondayBasedDayIndex(Calendar.MONDAY))
    }

    @Test
    fun `Calendar saturday maps to index 5 (Thứ 7)`() {
        assertEquals(5, mondayBasedDayIndex(Calendar.SATURDAY))
    }

    @Test
    fun `every Calendar day maps into a unique week slot`() {
        val indices = (Calendar.SUNDAY..Calendar.SATURDAY).map(::mondayBasedDayIndex)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), indices.sorted())
    }
}
