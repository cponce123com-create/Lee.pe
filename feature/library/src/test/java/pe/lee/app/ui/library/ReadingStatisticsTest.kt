package pe.lee.app.ui.library

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReadingStatisticsTest {
    private lateinit var previousZone: TimeZone

    @Before
    fun setUp() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(previousZone)
    }

    private fun instant(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 13, 30, 0)
        }.timeInMillis

    @Test
    fun activeStreakIncludesToday() {
        val now = instant(2026, 11, 2)
        assertEquals(
            3,
            calculateReadingStreak(listOf("2026-11-02", "2026-11-01", "2026-10-31"), now)
        )
    }

    @Test
    fun yesterdayStillCountsBeforeReadingToday() {
        val now = instant(2026, 11, 2)
        assertEquals(
            2,
            calculateReadingStreak(listOf("2026-11-01", "2026-10-31"), now)
        )
    }

    @Test
    fun missedYesterdayBreaksStreak() {
        val now = instant(2026, 11, 2)
        assertEquals(1, calculateReadingStreak(listOf("2026-11-02", "2026-10-31"), now))
        assertEquals(0, calculateReadingStreak(listOf("2026-10-31"), now))
    }

    @Test
    fun duplicateDaysDoNotInflateStreak() {
        val now = instant(2026, 11, 2)
        assertEquals(
            2,
            calculateReadingStreak(listOf("2026-11-02", "2026-11-02", "2026-11-01"), now)
        )
    }

    @Test
    fun dayKeyUsesDeviceCalendarTimezone() {
        val midnightLima = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 2, 0, 15)
        }.timeInMillis
        assertEquals("2026-11-02", readingDayKey(midnightLima))
    }
}
