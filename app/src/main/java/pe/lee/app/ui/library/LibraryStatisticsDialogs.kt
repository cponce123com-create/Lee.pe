package pe.lee.app.ui.library

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import pe.lee.app.data.repository.SettingsRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope



private const val DEFAULT_DAILY_GOAL_MINUTES = 30
private const val MIN_DAILY_GOAL_MINUTES = 5
private const val MAX_DAILY_GOAL_MINUTES = 240

private fun formatReadingDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    if (seconds <= 0L) return "0 min"
    if (totalMinutes == 0L) return "Menos de 1 min"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
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
    coroutineScope: CoroutineScope
) {
    if (showLibraryStats.value) {
        val readingCount = books.count { it.format != "Audio" && it.readProgress > 0f && it.readProgress < 1f }
        val completedCount = books.count { it.format != "Audio" && it.readProgress >= 1f }
        val favoriteCount = books.count { it.isFavorite }
        AlertDialog(
            onDismissRequest = { showLibraryStats.value = false },
            title = { Text("Estadísticas de biblioteca") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LibraryStatisticRow("Libros en total", books.size)
                    LibraryStatisticRow("En curso", readingCount)
                    LibraryStatisticRow("Terminados", completedCount)
                    LibraryStatisticRow("Favoritos", favoriteCount)
                    Text("Tiempo de lectura hoy: ${formatReadingDuration(todaySeconds)}")
                    Text("Racha actual: ${calculateReadingStreak(readingDayKeys, System.currentTimeMillis())} días")
                    Text("Meta diaria: ${dailyGoalMinutes.value} min")
                    LinearProgressIndicator(
                        progress = (todaySeconds.toFloat() / (dailyGoalMinutes.value * 60f)).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            goalDraft.value = dailyGoalMinutes.value.toString()
                            showLibraryStats.value = false
                            showGoalEditor.value = true
                        }) { Text("Cambiar meta") }
                        TextButton(onClick = { showResetStatsDialog.value = true }) { Text("Reiniciar historial") }
                    }
                    Text(
                        "El tiempo se registra localmente mientras un lector EPUB o PDF está en primer plano. No incluye audiolibros reproducidos en segundo plano.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLibraryStats.value = false }) { Text("Cerrar") }
            }
        )
    }

    if (showGoalEditor.value) {
        AlertDialog(
            onDismissRequest = {
                showGoalEditor.value = false
                showLibraryStats.value = true
            },
            title = { Text("Meta diaria de lectura") },
            text = {
                OutlinedTextField(
                    value = goalDraft.value,
                    onValueChange = { goalDraft.value = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minutos (5 a 240)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
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
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGoalEditor.value = false
                    showLibraryStats.value = true
                }) { Text("Cancelar") }
            }
        )
    }

    if (showResetStatsDialog.value) {
        AlertDialog(
            onDismissRequest = { showResetStatsDialog.value = false },
            title = { Text("Reiniciar tiempo de lectura") },
            text = { Text("Se borrará el historial local de tiempo y rachas. La biblioteca, el progreso y las anotaciones se conservarán.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        runCatching { viewModel.resetReadingStatistics() }
                            .onSuccess {
                                showResetStatsDialog.value = false
                                Toast.makeText(context, "Historial de lectura reiniciado", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "No se pudo reiniciar el historial", Toast.LENGTH_LONG).show()
                            }
                    }
                }) { Text("Reiniciar") }
            },
            dismissButton = {
                TextButton(onClick = { showResetStatsDialog.value = false }) { Text("Cancelar") }
            }
        )
    }

}
