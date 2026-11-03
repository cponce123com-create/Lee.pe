package pe.lee.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReadingSessionRecorderTest {
    private val lima = ZoneId.of("America/Lima")

    @Test
    fun splitsElapsedTimeAtLocalMidnight() {
        val start = LocalDate.of(2026, 11, 2)
            .atTime(23, 59, 50)
            .atZone(lima)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            listOf("2026-11-02" to 10L, "2026-11-03" to 20L),
            splitSecondsByLocalDay(start, 30L, lima)
        )
    }

    @Test
    fun leavesSingleDaySessionUnsplit() {
        val start = LocalDate.of(2026, 11, 2)
            .atTime(18, 0)
            .atZone(lima)
            .toInstant()
            .toEpochMilli()

        assertEquals(listOf("2026-11-02" to 45L), splitSecondsByLocalDay(start, 45L, lima))
    }

    @Test
    fun fractionalSecondAtMidnightIsAssignedToTheCorrectCalendarDay() {
        val start = LocalDate.of(2026, 11, 2)
            .atTime(23, 59, 30, 500_000_000)
            .atZone(lima)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            listOf("2026-11-02" to 30L, "2026-11-03" to 1L),
            splitSecondsByLocalDay(start, 31L, lima)
        )
    }

    @Test
    fun ignoresEmptyDuration() {
        assertEquals(emptyList<Pair<String, Long>>(), splitSecondsByLocalDay(0L, 0L, lima))
    }
}
