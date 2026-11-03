package pe.lee.app.ui.library

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import pe.lee.app.data.local.BookEntity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope



@Composable
internal fun LibraryProcessDialogs(
    context: Context,
    viewModel: LibraryViewModel,
    coroutineScope: CoroutineScope,
    storedBooks: List<BookEntity>,
    scannerBusy: MutableState<Boolean>,
    scannerJob: MutableState<Job?>,
    scannerFolderName: String,
    scannerProgress: LocalFolderScanner.Progress,
    scannerPreview: MutableState<LocalFolderScanner.Result?>,
    scannerImporting: MutableState<Boolean>,
    showBackupOptions: MutableState<Boolean>,
    backupBusy: MutableState<Boolean>,
    backupPreview: MutableState<LocalBackupPreview?>,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    if (scannerBusy.value) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Buscando libros locales") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(scannerFolderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Carpetas: ${scannerProgress.folders} · " +
                            "Archivos examinados: ${scannerProgress.files} · " +
                            "Libros encontrados: ${scannerProgress.books}"
                    )
                    Text("La búsqueda puede cancelarse. No se sube ningún archivo a internet.")
                }
            },
            confirmButton = {
                TextButton(onClick = { scannerJob.value?.cancel(); scannerBusy.value = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    scannerPreview.value?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!scannerImporting.value) scannerPreview.value = null },
            title = { Text("Resultado del escáner") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nuevos libros: ${preview.books.size}")
                    Text("Ya registrados: ${preview.duplicates}")
                    Text("Archivos examinados: ${preview.visitedFiles}")
                    Text("Carpetas examinadas: ${preview.visitedFolders}")
                    if (preview.skippedFolders > 0) {
                        Text("Carpetas no accesibles u omitidas: ${preview.skippedFolders}")
                    }
                    if (preview.limitReached) {
                        Text(
                            "Se alcanzó el límite de seguridad del escaneo. " +
                                "Puedes repetirlo seleccionando una subcarpeta.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        "Se incorporarán referencias a tus archivos EPUB, PDF y audio. " +
                            "Los archivos permanecerán en su carpeta original y no se copiarán ni subirán."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !scannerImporting.value && preview.books.isNotEmpty(),
                    onClick = {
                        scannerImporting.value = true
                        coroutineScope.launch {
                            try {
                                val now = System.currentTimeMillis()
                                val entries = preview.books.map { item ->
                                    BookEntity(
                                        uri = item.uri.toString(),
                                        title = item.title,
                                        format = item.format,
                                        importedAt = now
                                    )
                                }
                                viewModel.insertBooks(entries)
                                Toast.makeText(
                                    context,
                                    "Escáner: se incorporaron hasta ${entries.size} libros a la biblioteca.",
                                    Toast.LENGTH_LONG
                                ).show()
                                scannerPreview.value = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Toast.makeText(
                                    context, error.message ?: "No se pudieron importar los libros.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                scannerImporting.value = false
                            }
                        }
                    }
                ) { Text(if (scannerImporting.value) "Importando…" else "Añadir a biblioteca") }
            },
            dismissButton = {
                TextButton(
                    enabled = !scannerImporting.value,
                    onClick = { scannerPreview.value = null }
                ) { Text("Cancelar") }
            }
        )
    }

    if (showBackupOptions.value) {
        AlertDialog(
            onDismissRequest = { if (!backupBusy.value) showBackupOptions.value = false },
            title = { Text("Respaldo local de Lee.pe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exporta o recupera tu biblioteca, ubicaciones, notas, marcadores y estadísticas.")
                    Text(
                        "Los archivos EPUB, PDF y audio no se incluyen. En otro teléfono primero " +
                            "importa los libros y después recupera los datos. Las coincidencias " +
                            "por título pueden variar entre ediciones.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "El respaldo JSON contiene notas y datos de lectura sin cifrar. " +
                            "Guárdalo en un lugar privado. Nunca se envía a Lee.pe.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !backupBusy.value, onClick = {
                    showBackupOptions.value = false
                    onExportBackup()
                }) { Text("Exportar") }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !backupBusy.value, onClick = {
                        showBackupOptions.value = false
                        onImportBackup()
                    }) { Text("Restaurar") }
                    TextButton(onClick = { showBackupOptions.value = false }) { Text("Cancelar") }
                }
            }
        )
    }

    backupPreview.value?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!backupBusy.value) backupPreview.value = null },
            title = { Text("Confirmar restauración") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Libros del respaldo: ${selected.archive.snapshot.books.size}")
                    Text("Coincidencias exactas: ${selected.exactMatches}")
                    Text("Por título y formato: ${selected.titleMatches}")
                    Text("Sin un libro local correspondiente: ${selected.unavailable}")
                    Text("Notas del respaldo: ${selected.archive.snapshot.annotations.size}")
                    Text(
                        "Solo se incorporarán datos de libros que ya estén en tu biblioteca. " +
                            "No se borrarán libros ni se sobrescribirán anotaciones existentes. " +
                            "Comprueba que las coincidencias por título sean la misma edición. " +
                            "Podrás volver a restaurar el archivo después de importar libros faltantes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !backupBusy.value && selected.exactMatches + selected.titleMatches > 0,
                    onClick = {
                        backupBusy.value = true
                        coroutineScope.launch {
                            try {
                                val report = LocalLibraryBackup.restore(context, selected)
                                Toast.makeText(
                                    context,
                                    "Recuperados ${report.linkedBooks} libros, ${report.addedAnnotations} anotaciones. " +
                                        "Pendientes: ${report.skippedBooks}",
                                    Toast.LENGTH_LONG
                                ).show()
                                backupPreview.value = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "No se pudo restaurar el respaldo", Toast.LENGTH_LONG).show()
                            } finally {
                                backupBusy.value = false
                            }
                        }
                    }
                ) { Text(if (backupBusy.value) "Restaurando…" else "Restaurar datos") }
            },
            dismissButton = {
                TextButton(enabled = !backupBusy.value, onClick = { backupPreview.value = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

}
