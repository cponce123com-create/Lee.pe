package pe.lee.app.ui.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import pe.lee.app.data.local.ReadingSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class ReadingSessionRecorder(
    private val bookUri: String,
    private val dao: ReadingSessionDao
) {
    private val handler = Handler(Looper.getMainLooper())
    private var checkpointElapsedMs: Long? = null

    private val persistRunnable = object : Runnable {
        override fun run() {
            persistElapsed()
            if (checkpointElapsedMs != null) {
                handler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
            }
        }
    }

    fun onResume() {
        if (checkpointElapsedMs != null) return
        checkpointElapsedMs = SystemClock.elapsedRealtime()
        handler.postDelayed(persistRunnable, CHECKPOINT_INTERVAL_MS)
    }

    fun onPause() {
        handler.removeCallbacks(persistRunnable)
        persistElapsed()
        checkpointElapsedMs = null
    }

    private fun persistElapsed() {
        val startedAt = checkpointElapsedMs ?: return
        val now = SystemClock.elapsedRealtime()
        val seconds = (now - startedAt) / 1_000L
        if (seconds <= 0L) return

        checkpointElapsedMs = startedAt + seconds * 1_000L
        val dayKey = SimpleDateFormat(DAY_KEY_PATTERN, Locale.US).format(Date())
        ioScope.launch {
            runCatching { dao.addDuration(dayKey, bookUri, seconds) }
                .onFailure { Log.w(TAG, "Could not save local reading time", it) }
        }
    }

    private companion object {
        const val CHECKPOINT_INTERVAL_MS = 30_000L
        const val DAY_KEY_PATTERN = "yyyy-MM-dd"
        const val TAG = "LeePeReadingTime"
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
