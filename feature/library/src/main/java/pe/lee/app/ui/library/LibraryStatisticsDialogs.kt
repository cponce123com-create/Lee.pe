package pe.lee.app.ui.library

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.feature.library.R

private const val DEFAULT_DAILY_GOAL_MINUTES = 30
private const val MIN_DAILY_GOAL_MINUTES = 5
private const val MAX_DAILY_GOAL_MINUTES = 240

private fun formatReadingDuration(context: Context, seconds: Long): String {
    val totalMinutes = seconds / 60
    if (seconds <= 0L) return context.getString(R.string.reading_duration_zero)
    if (totalMinutes == 0L) return context.getString(R.string.reading_duration_under_minute)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        if (minutes == 0L) {
            context.getString(R.string.reading_duration_hours, hours)
        } else {
            context.getString(R.string.reading_duration_hours_minutes, hours, minutes)
        }
    } else {
        context.getString(R.string.reading_duration_minutes, minutes)
    }
}

@Composable
internal fun LibraryStatisticsDialogs(
    books: List<LocalBook>,
    todaySeconds: Long,
    readingDayKeys: List<String>,
    showLibraryStats: MutableState<Boolean>,
    showGoalEditor: MutableState<Boolean>,
    showResetStatsDialog: MutableState<Boolean>,
    goalDraft: MutableState<String>,
    dailyGoalMinutes: MutableState<Int>,
    settings: SettingsRepository,
    viewModel: LibraryViewModel,
    context: Context,
    coroutineScope: CoroutineScope,
) {
    if (showLibraryStats.value) {
        val currentStreak = calculateReadingStreak(readingDayKeys, System.currentTimeMillis())
        val readingCount = books.count { it.format != "Audio" && it.readProgress > 0f && it.readProgress < 1f }
        val completedCount = books.count { it.format != "Audio" && it.readProgress >= 1f }
        val favoriteCount = books.count { it.isFavorite }
        AlertDialog(
            onDismissRequest = { showLibraryStats.value = false },
            title = { Text(stringResource(R.string.library_statistics_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LibraryStatisticRow(stringResource(R.string.library_stat_total_books), books.size)
                    LibraryStatisticRow(stringResource(R.string.library_stat_in_progress), readingCount)
                    LibraryStatisticRow(stringResource(R.string.library_stat_completed), completedCount)
                    LibraryStatisticRow(stringResource(R.string.library_stat_favorites), favoriteCount)
                    Text(stringResource(R.string.reading_time_today, formatReadingDuration(context, todaySeconds)))
                    Text(
                        pluralStringResource(
                            R.plurals.reading_streak_days,
                            currentStreak,
                            currentStreak,
                        ),
                    )
                    Text(stringResource(R.string.reading_daily_goal, dailyGoalMinutes.value))
                    LinearProgressIndicator(
                        progress = { (todaySeconds.toFloat() / (dailyGoalMinutes.value * 60f)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = {
                            goalDraft.value = dailyGoalMinutes.value.toString()
                            showLibraryStats.value = false
                            showGoalEditor.value = true
                        }) { Text(stringResource(R.string.reading_change_goal)) }
                        TextButton(onClick = { showResetStatsDialog.value = true }) { Text(stringResource(R.string.reading_reset_history)) }
                    }
                    Text(
                        stringResource(R.string.reading_stats_tracking_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLibraryStats.value = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (showGoalEditor.value) {
        AlertDialog(
            onDismissRequest = {
                showGoalEditor.value = false
                showLibraryStats.value = true
            },
            title = { Text(stringResource(R.string.reading_daily_goal_title)) },
            text = {
                OutlinedTextField(
                    value = goalDraft.value,
                    onValueChange = { goalDraft.value = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.reading_goal_minutes_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = goalDraft.value.toIntOrNull()?.let { it in MIN_DAILY_GOAL_MINUTES..MAX_DAILY_GOAL_MINUTES } == true,
                    onClick = {
                        val goal = goalDraft.value.toIntOrNull() ?: return@TextButton
                        dailyGoalMinutes.value = goal
                        settings.apply(SettingsRepository.Store.READING_STATS) {
                            putInt("daily_goal_minutes", goal)
                        }
                        showGoalEditor.value = false
                        showLibraryStats.value = true
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGoalEditor.value = false
                    showLibraryStats.value = true
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showResetStatsDialog.value) {
        AlertDialog(
            onDismissRequest = { showResetStatsDialog.value = false },
            title = { Text(stringResource(R.string.reading_reset_title)) },
            text = { Text(stringResource(R.string.reading_reset_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        runCatching { viewModel.resetReadingStatistics() }
                            .onSuccess {
                                showResetStatsDialog.value = false
                                Toast.makeText(context, context.getString(R.string.reading_history_reset), Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, context.getString(R.string.reading_history_reset_failed), Toast.LENGTH_LONG).show()
                            }
                    }
                }) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetStatsDialog.value = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
