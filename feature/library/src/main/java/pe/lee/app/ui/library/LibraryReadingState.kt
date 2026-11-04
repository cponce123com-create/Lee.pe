package pe.lee.app.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

internal data class LibraryReadingState(val todaySeconds: Long, val dayKeys: List<String>)

/** Refreshes the observed day at midnight and collects only while the screen is active. */
@Composable
internal fun rememberLibraryReadingState(viewModel: LibraryViewModel): LibraryReadingState {
    val todayKeyState = remember { mutableStateOf(readingDayKey(System.currentTimeMillis())) }
    val todayKey by todayKeyState
    // "Today" was computed once per composition: leaving the app open past
    // midnight kept showing yesterday's time and goal (A-37).
    LaunchedEffect(todayKey) {
        val now = System.currentTimeMillis()
        val nextMidnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        delay((nextMidnight - now).coerceAtLeast(1_000L))
        todayKeyState.value = readingDayKey(System.currentTimeMillis())
    }
    // A fresh Flow instance per recomposition restarted the Room subscription on
    // every keystroke (P-01); remember it for the lifetime of the day.
    val todaySecondsFlow = remember(viewModel, todayKey) { viewModel.observeDurationForDay(todayKey) }
    val readingDayKeysFlow = remember(viewModel) { viewModel.observeReadingDayKeys() }
    val todaySeconds by todaySecondsFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val readingDayKeys by readingDayKeysFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    return LibraryReadingState(todaySeconds, readingDayKeys)
}
