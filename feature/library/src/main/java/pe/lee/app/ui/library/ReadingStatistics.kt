package pe.lee.app.ui.library

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * All reading statistics are computed on-device. Use calendar days rather than
 * subtracting fixed 24-hour intervals to handle daylight-saving time correctly.
 */
internal fun readingDayKey(timestampMillis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMillis))

/**
 * A streak can start today, or yesterday if the user has not read yet today.
 * Missing days break the streak; duplicate database day keys count once.
 */
internal fun calculateReadingStreak(dayKeys: List<String>, nowMillis: Long): Int {
    val recordedDays = dayKeys.toSet()
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    if (readingDayKey(nowMillis) !in recordedDays) {
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    var streak = 0
    while (readingDayKey(calendar.timeInMillis) in recordedDays) {
        streak++
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}
