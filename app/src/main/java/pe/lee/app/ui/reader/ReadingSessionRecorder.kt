package pe.lee.app.ui.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pe.lee.app.data.repository.ReadingStatsRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

internal class ReadingSessionRecorder(
    private val bookUri: String,
    private val repository: ReadingStatsRepository
) {
    // Each reader owns its short-lived persistence scope; there is no process-wide scope.
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var checkpointElapsedMs: Long? = null
    private var checkpointWallMs: Long? = null
    @Volatile private var closed = false
    private val pendingWrites = AtomicInteger(0)

    private val persistRunnable = object : Runnable {
        override fun run() {
            persistElapsed()
            if (checkpointElapsedMs != null && !closed) {
                handler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
            }
        }
    }

    fun onResume() {
        if (closed || checkpointElapsedMs != null) return
        checkpointElapsedMs = SystemClock.elapsedRealtime()
        checkpointWallMs = System.currentTimeMillis()
        handler.postDelayed(persistRunnable, CHECKPOINT_INTERVAL_MS)
    }

    fun onPause() {
        handler.removeCallbacks(persistRunnable)
        persistElapsed()
        checkpointElapsedMs = null
        checkpointWallMs = null
    }

    fun close() {
        if (closed) return
        onPause()
        closed = true
        if (pendingWrites.get() == 0) persistenceScope.cancel()
    }

    private fun persistElapsed() {
        val startedElapsedMs = checkpointElapsedMs ?: return
        val startedWallMs = checkpointWallMs ?: return
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val seconds = (nowElapsedMs - startedElapsedMs) / MILLIS_PER_SECOND
        if (seconds <= 0L) return

        val elapsedMs = seconds * MILLIS_PER_SECOND
        checkpointElapsedMs = startedElapsedMs + elapsedMs
        checkpointWallMs = startedWallMs + elapsedMs
        val dailyDurations = splitSecondsByLocalDay(
            startEpochMillis = startedWallMs,
            durationSeconds = seconds,
            zoneId = ZoneId.systemDefault()
        )
        pendingWrites.incrementAndGet()
        persistenceScope.launch {
            try {
                dailyDurations.forEach { (dayKey, daySeconds) ->
                    runCatching { repository.addDuration(dayKey, bookUri, daySeconds) }
                        .onFailure { Log.w(TAG, "Could not save local reading time", it) }
                }
            } finally {
                if (pendingWrites.decrementAndGet() == 0 && closed) persistenceScope.cancel()
            }
        }
    }

    private companion object {
        const val CHECKPOINT_INTERVAL_MS = 30_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val TAG = "LeePeReadingTime"
    }
}

internal fun splitSecondsByLocalDay(
    startEpochMillis: Long,
    durationSeconds: Long,
    zoneId: ZoneId
): List<Pair<String, Long>> {
    if (durationSeconds <= 0L) return emptyList()
    val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT)
    var cursor = Instant.ofEpochMilli(startEpochMillis)
    var remainingSeconds = durationSeconds
    val result = mutableListOf<Pair<String, Long>>()

    while (remainingSeconds > 0L) {
        val localStart = cursor.atZone(zoneId)
        val nextMidnight = localStart.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        val millisUntilMidnight = Duration.between(cursor, nextMidnight).toMillis()
        val secondsUntilMidnight = if (millisUntilMidnight <= 0L) 0L
            else (millisUntilMidnight + 999L) / 1_000L
        val segmentSeconds = if (secondsUntilMidnight <= 0L) {
            remainingSeconds
        } else {
            minOf(remainingSeconds, secondsUntilMidnight)
        }
        val dayKey = localStart.toLocalDate().format(dayFormatter)
        val last = result.lastOrNull()
        if (last?.first == dayKey) {
            result[result.lastIndex] = dayKey to (last.second + segmentSeconds)
        } else {
            result += dayKey to segmentSeconds
        }
        cursor = cursor.plusSeconds(segmentSeconds)
        remainingSeconds -= segmentSeconds
    }
    return result
}
