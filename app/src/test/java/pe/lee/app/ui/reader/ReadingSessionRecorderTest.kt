package pe.lee.app.ui.reader

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

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
            splitSecondsByLocalDay(start, 30L, lima),
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
            splitSecondsByLocalDay(start, 31L, lima),
        )
    }

    @Test
    fun ignoresEmptyDuration() {
        assertEquals(emptyList<Pair<String, Long>>(), splitSecondsByLocalDay(0L, 0L, lima))
    }

    @Test
    fun daylightSavingDaysPreserveActualElapsedSeconds() {
        val zone = ZoneId.of("America/New_York")
        for ((date, hours) in listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L)) {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val duration = hours * 3_600L
            assertEquals(
                listOf(date.toString() to duration, date.plusDays(1).toString() to 60L),
                splitSecondsByLocalDay(start, duration + 60L, zone),
            )
        }
    }

    @Test
    fun multiDaySessionPreservesAllSeconds() {
        val date = LocalDate.of(2026, 11, 2)
        val start = date.atStartOfDay(lima).toInstant().toEpochMilli()
        assertEquals(
            listOf("2026-11-02" to 86_400L, "2026-11-03" to 86_400L, "2026-11-04" to 1L),
            splitSecondsByLocalDay(start, 172_801L, lima),
        )
        assertEquals(emptyList<Pair<String, Long>>(), splitSecondsByLocalDay(start, -1L, lima))
    }
}
