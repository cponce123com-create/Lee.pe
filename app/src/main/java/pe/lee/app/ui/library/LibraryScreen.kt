package pe.lee.app.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import pe.lee.app.ui.reader.PdfReaderActivity
import pe.lee.app.ui.reader.ReaderActivity
import pe.lee.app.ui.audio.AudioPlayerActivity
import pe.lee.app.data.local.BookDao
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.LeeDatabase
import pe.lee.app.data.local.OfflineBookStore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "lee_pe_library"
private const val READING_STATS_PREFS_NAME = "lee_pe_reading_stats"
private const val DEFAULT_DAILY_GOAL_MINUTES = 30
private const val MIN_DAILY_GOAL_MINUTES = 5
private const val MAX_DAILY_GOAL_MINUTES = 240
private val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "wav", "flac")
private const val KEY_DOCUMENT_URIS = "document_uris"
private const val KEY_LEGACY_MIGRATED = "room_library_migrated"

private fun formatReadingDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    if (seconds <= 0L) return "0 min"
    if (totalMinutes == 0L) return "Menos de 1 min"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
}


private data class LocalBook(
    val uri: Uri,
    val title: String,
    val format: String,
    val isFavorite: Boolean,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val readProgress: Float,
    val collectionId: String?,
    val offlineCopyUri: Uri?
)

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val database = remember(context) { LeeDatabase.get(context) }
    val dao = remember(database) { database.bookDao() }
    val annotationDao = remember(database) { database.readingAnnotationDao() }
    val readingSessionDao = remember(database) { database.readingSessionDao() }
    val todayKey = readingDayKey(System.currentTimeMillis())
    val todaySeconds by readingSessionDao.observeDurationForDay(todayKey).collectAsState(initial = 0L)
    val readingDayKeys by readingSessionDao.observeReadingDayKeys().collectAsState(initial = emptyList())
    val storedBooks by dao.observeAll().collectAsState(initial = emptyList())
    var offlineCopyUris by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var offlineRevision by remember { mutableIntStateOf(0) }
    var savingBookUri by remember { mutableStateOf<String?>(null) }
    val bookUris = remember(storedBooks) { storedBooks.map { Uri.parse(it.uri) } }
    // A locator/progress update invalidates Room, but does not change the URI list.
    // Only rescan the private offline directory when membership/copies change.
    LaunchedEffect(context, bookUris, offlineRevision) {
        offlineCopyUris = withContext(Dispatchers.IO) {
            OfflineBookStore.findCopies(context, bookUris)
                .mapValues { (_, file) -> Uri.fromFile(file) }
        }
    }
    val books = remember(storedBooks, offlineCopyUris) {
        storedBooks.map {
            LocalBook(Uri.parse(it.uri), it.title, it.format, it.isFavorite, it.importedAt, it.lastOpenedAt,
                it.readProgress, it.collectionId, offlineCopyUris[it.uri])
        }
    }
    val collections = remember(books) {
        books.mapNotNull { it.collectionId?.trim()?.takeIf { name -> name.isNotBlank() } }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Todos") }
    var selectedCollection by remember { mutableStateOf<String?>(null) }
    var collectionMenuExpanded by remember { mutableStateOf(false) }
    var organizingBook by remember { mutableStateOf<LocalBook?>(null) }
    var collectionDraft by remember { mutableStateOf("") }
    var showLibraryStats by remember { mutableStateOf(false) }
    var showGoalEditor by remember { mutableStateOf(false) }
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var goalDraft by remember { mutableStateOf("") }
    var dailyGoalMinutes by remember {
        mutableIntStateOf(
            context.getSharedPreferences(READING_STATS_PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("daily_goal_minutes", DEFAULT_DAILY_GOAL_MINUTES)
                .coerceIn(MIN_DAILY_GOAL_MINUTES, MAX_DAILY_GOAL_MINUTES)
        )
    }
    var pendingRemoval by remember { mutableStateOf<LocalBook?>(null) }
    var editingBook by remember { mutableStateOf<LocalBook?>(null) }
    var titleDraft by remember { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf("Título A–Z") }
    var pendingMarkdown by remember { mutableStateOf<String?>(null) }
    var libraryMenuExpanded by remember { mutableStateOf(false) }
    var showBackupOptions by remember { mutableStateOf(false) }
    var backupPreview by remember { mutableStateOf<LocalBackupPreview?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var scannerBusy by remember { mutableStateOf(false) }
    var scannerJob by remember { mutableStateOf<Job?>(null) }
    var scannerProgress by remember { mutableStateOf(LocalFolderScanner.Progress(0, 0, 0)) }
    var scannerPreview by remember { mutableStateOf<LocalFolderScanner.Result?>(null) }
    var scannerImporting by remember { mutableStateOf(false) }
    var scannerFolderName by remember { mutableStateOf("Carpeta seleccionada") }
    var listMode by remember { mutableStateOf(false) }

    LaunchedEffect(context, dao) {
        migrateLegacyLibrary(context, dao)
    }

    val exportAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val markdown = pendingMarkdown
        pendingMarkdown = null
        if (uri != null && markdown != null) {
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IOException("No se pudo crear el archivo de exportación.")
                        output.bufferedWriter(Charsets.UTF_8).use { it.write(markdown) }
                    }
                    Toast.makeText(context, "Anotaciones exportadas en Markdown", Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "No se pudo exportar las anotaciones", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destination ->
        if (destination != null) coroutineScope.launch {
            backupBusy = true
            try {
                LocalLibraryBackup.save(context, destination)
                Toast.makeText(context, "Respaldo local exportado correctamente", Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "No se pudo exportar el respaldo", Toast.LENGTH_LONG).show()
            } finally {
                backupBusy = false
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) coroutineScope.launch {
            backupBusy = true
            try {
                backupPreview = LocalLibraryBackup.preview(context, picked)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Respaldo no válido", Toast.LENGTH_LONG).show()
            } finally {
                backupBusy = false
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) coroutineScope.launch {
            val current = storedBooks.mapTo(HashSet()) { it.uri }
            var alreadyPresent = 0
            var unsupported = 0
            var noPermission = 0
            val picked = selectedUris.distinctBy { it.toString() }
            try {
                val newBooks = withContext(Dispatchers.IO) {
                    picked.mapNotNull { uri ->
                        if (uri.toString() in current) {
                            alreadyPresent++
                            return@mapNotNull null
                        }
                        val fileName = runCatching { queryDisplayName(context, uri) }
                            .getOrNull()
                        if (fileName == null) {
                            unsupported++
                            return@mapNotNull null
                        }
                        val format = formatFromFileName(fileName)
                        if (format == null) {
                            unsupported++
                            return@mapNotNull null
                        }
                        val permitted = runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }.isSuccess
                        if (!permitted) {
                            noPermission++
                            return@mapNotNull null
                        }
                        BookEntity(
                            uri = uri.toString(),
                            title = fileName.substringBeforeLast('.', fileName).ifBlank { fileName },
                            format = format,
                            importedAt = System.currentTimeMillis()
                        )
                    }
                }
                if (newBooks.isNotEmpty()) {
                    dao.insertAll(newBooks)
                }
                Toast.makeText(
                    context,
                    "Archivos incorporados: ${newBooks.size}. " +
                        "Ya existentes: $alreadyPresent. " +
                        "No compatibles: $unsupported. " +
                        "Sin permiso persistente: $noPermission.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "No se pudo completar la importación.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val directoryScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            if (!granted) {
                Toast.makeText(
                    context, "No se pudo conservar el permiso de la carpeta.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                scannerPreview = null
                scannerProgress = LocalFolderScanner.Progress(0, 0, 0)
                scannerBusy = true
                scannerFolderName = treeUri.lastPathSegment?.substringAfterLast(':')
                    ?.takeIf { it.isNotBlank() } ?: "Carpeta seleccionada"
                scannerJob = coroutineScope.launch {
                    try {
                        val existing = storedBooks.map { it.uri }
                        scannerPreview = LocalFolderScanner.scan(context, treeUri, existing) { progress ->
                            withContext(Dispatchers.Main.immediate) { scannerProgress = progress }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Toast.makeText(
                            context, error.message ?: "No se pudo escanear la carpeta.",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        scannerBusy = false
                        scannerJob = null
                    }
                }
            }
        }
    }

    if (scannerBusy) {
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
                TextButton(onClick = { scannerJob?.cancel(); scannerBusy = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    scannerPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!scannerImporting) scannerPreview = null },
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
                    enabled = !scannerImporting && preview.books.isNotEmpty(),
                    onClick = {
                        scannerImporting = true
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
                                dao.insertAll(entries)
                                Toast.makeText(
                                    context,
                                    "Escáner: se incorporaron hasta ${entries.size} libros a la biblioteca.",
                                    Toast.LENGTH_LONG
                                ).show()
                                scannerPreview = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Toast.makeText(
                                    context, error.message ?: "No se pudieron importar los libros.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                scannerImporting = false
                            }
                        }
                    }
                ) { Text(if (scannerImporting) "Importando…" else "Añadir a biblioteca") }
            },
            dismissButton = {
                TextButton(
                    enabled = !scannerImporting,
                    onClick = { scannerPreview = null }
                ) { Text("Cancelar") }
            }
        )
    }

    if (showBackupOptions) {
        AlertDialog(
            onDismissRequest = { if (!backupBusy) showBackupOptions = false },
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
                TextButton(enabled = !backupBusy, onClick = {
                    showBackupOptions = false
                    backupExportLauncher.launch("Lee.pe-respaldo-${readingDayKey(System.currentTimeMillis())}.json")
                }) { Text("Exportar") }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !backupBusy, onClick = {
                        showBackupOptions = false
                        backupImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }) { Text("Restaurar") }
                    TextButton(onClick = { showBackupOptions = false }) { Text("Cancelar") }
                }
            }
        )
    }

    backupPreview?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!backupBusy) backupPreview = null },
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
                    enabled = !backupBusy && selected.exactMatches + selected.titleMatches > 0,
                    onClick = {
                        backupBusy = true
                        coroutineScope.launch {
                            try {
                                val report = LocalLibraryBackup.restore(context, selected)
                                Toast.makeText(
                                    context,
                                    "Recuperados ${report.linkedBooks} libros, ${report.addedAnnotations} anotaciones. " +
                                        "Pendientes: ${report.skippedBooks}",
                                    Toast.LENGTH_LONG
                                ).show()
                                backupPreview = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "No se pudo restaurar el respaldo", Toast.LENGTH_LONG).show()
                            } finally {
                                backupBusy = false
                            }
                        }
                    }
                ) { Text(if (backupBusy) "Restaurando…" else "Restaurar datos") }
            },
            dismissButton = {
                TextButton(enabled = !backupBusy, onClick = { backupPreview = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showLibraryStats) {
        val readingCount = books.count { it.format != "Audio" && it.readProgress > 0f && it.readProgress < 1f }
        val completedCount = books.count { it.format != "Audio" && it.readProgress >= 1f }
        val favoriteCount = books.count { it.isFavorite }
        AlertDialog(
            onDismissRequest = { showLibraryStats = false },
            title = { Text("Estadísticas de biblioteca") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LibraryStatisticRow("Libros en total", books.size)
                    LibraryStatisticRow("En curso", readingCount)
                    LibraryStatisticRow("Terminados", completedCount)
                    LibraryStatisticRow("Favoritos", favoriteCount)
                    Text("Tiempo de lectura hoy: ${formatReadingDuration(todaySeconds)}")
                    Text("Racha actual: ${calculateReadingStreak(readingDayKeys, System.currentTimeMillis())} días")
                    Text("Meta diaria: $dailyGoalMinutes min")
                    LinearProgressIndicator(
                        progress = (todaySeconds.toFloat() / (dailyGoalMinutes * 60f)).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            goalDraft = dailyGoalMinutes.toString()
                            showLibraryStats = false
                            showGoalEditor = true
                        }) { Text("Cambiar meta") }
                        TextButton(onClick = { showResetStatsDialog = true }) { Text("Reiniciar historial") }
                    }
                    Text(
                        "El tiempo se registra localmente mientras un lector EPUB o PDF está en primer plano. No incluye audiolibros reproducidos en segundo plano.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLibraryStats = false }) { Text("Cerrar") }
            }
        )
    }

    if (showGoalEditor) {
        AlertDialog(
            onDismissRequest = {
                showGoalEditor = false
                showLibraryStats = true
            },
            title = { Text("Meta diaria de lectura") },
            text = {
                OutlinedTextField(
                    value = goalDraft,
                    onValueChange = { goalDraft = it.filter(Char::isDigit).take(3) },
                    label = { Text("Minutos (5 a 240)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = goalDraft.toIntOrNull()?.let { it in MIN_DAILY_GOAL_MINUTES..MAX_DAILY_GOAL_MINUTES } == true,
                    onClick = {
                        val goal = goalDraft.toIntOrNull() ?: return@TextButton
                        dailyGoalMinutes = goal
                        context.getSharedPreferences(READING_STATS_PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putInt("daily_goal_minutes", goal).apply()
                        showGoalEditor = false
                        showLibraryStats = true
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGoalEditor = false
                    showLibraryStats = true
                }) { Text("Cancelar") }
            }
        )
    }

    if (showResetStatsDialog) {
        AlertDialog(
            onDismissRequest = { showResetStatsDialog = false },
            title = { Text("Reiniciar tiempo de lectura") },
            text = { Text("Se borrará el historial local de tiempo y rachas. La biblioteca, el progreso y las anotaciones se conservarán.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        runCatching { readingSessionDao.deleteAll() }
                            .onSuccess {
                                showResetStatsDialog = false
                                Toast.makeText(context, "Historial de lectura reiniciado", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "No se pudo reiniciar el historial", Toast.LENGTH_LONG).show()
                            }
                    }
                }) { Text("Reiniciar") }
            },
            dismissButton = {
                TextButton(onClick = { showResetStatsDialog = false }) { Text("Cancelar") }
            }
        )
    }

    pendingRemoval?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Quitar de la biblioteca") },
            text = {
                Text(
                    "Se quitará “${book.title}” de Lee.pe y se borrarán sus marcadores, notas y " +
                        "resaltados guardados. El archivo original no se eliminará del dispositivo." +
                        if (book.offlineCopyUri != null) " La copia sin conexión guardada en Lee.pe también se eliminará." else ""
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            dao.deleteByUri(book.uri.toString())
                            withContext(Dispatchers.IO) { OfflineBookStore.deleteCopy(context, book.uri) }
                            pendingRemoval = null
                            offlineRevision++
                            Toast.makeText(context, "Libro quitado de la biblioteca", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancelar") }
            }
        )
    }

    organizingBook?.let { book ->
        AlertDialog(
            onDismissRequest = { organizingBook = null },
            title = { Text("Colección del libro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        book.title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (collections.isNotEmpty()) {
                        Text("Mover a una colección", fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            collections.forEach { collection ->
                                FilterChip(
                                    selected = book.collectionId == collection,
                                    onClick = {
                                        coroutineScope.launch {
                                            dao.updateCollection(book.uri.toString(), collection)
                                            organizingBook = null
                                        }
                                    },
                                    label = { Text(collection) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = collectionDraft,
                        onValueChange = { collectionDraft = it },
                        label = { Text("Crear o usar colección") },
                        placeholder = { Text("Ej. Fantasía, Por leer") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = collectionDraft.isNotBlank(),
                    onClick = {
                        val name = collectionDraft.trim()
                        coroutineScope.launch {
                            runCatching { dao.updateCollection(book.uri.toString(), name) }
                                .onSuccess {
                                    organizingBook = null
                                    Toast.makeText(context, "Libro organizado en “$name”", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "No se pudo guardar la colección", Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (book.collectionId != null) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    dao.updateCollection(book.uri.toString(), null)
                                    organizingBook = null
                                }
                            }
                        ) { Text("Quitar") }
                    }
                    TextButton(onClick = { organizingBook = null }) { Text("Cancelar") }
                }
            }
        )
    }

    editingBook?.let { book ->
        AlertDialog(
            onDismissRequest = { editingBook = null },
            title = { Text("Editar título") },
            text = {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    label = { Text("Título del libro") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = titleDraft.isNotBlank(),
                    onClick = {
                        coroutineScope.launch {
                            runCatching { dao.updateTitle(book.uri.toString(), titleDraft.trim()) }
                                .onSuccess {
                                    editingBook = null
                                    Toast.makeText(context, "Título actualizado", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "No se pudo actualizar el título", Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editingBook = null }) { Text("Cancelar") }
            }
        )
    }

    val filters = listOf("Todos", "Por leer", "En curso", "Terminados", "Favoritos", "EPUB", "PDF", "Audio", "Copia en Lee.pe")
    // Cache catalog calculations across unrelated UI recompositions (menus, dialogs,
    // stats updates, etc.). Everything stays on-device; no search API is required.
    val normalizedQuery = remember(query) { normalizedLibraryText(query) }
    // Precompute accents/format/collection once instead of normalizing every
    // title again for each keystroke in a large library.
    val searchIndex = remember(books) {
        books.associate { book ->
            book.uri.toString() to normalizedLibraryText(
                "${book.title} ${book.format} ${book.collectionId.orEmpty()}"
            )
        }
    }
    val visibleBooks = remember(books, searchIndex, normalizedQuery, selectedFilter, selectedCollection) {
        books.filter { book ->
            val matchesQuery = matchesNormalizedLibraryQuery(
                searchIndex[book.uri.toString()].orEmpty(), normalizedQuery
            )
            val matchesFilter = when (selectedFilter) {
                "Por leer" -> book.format != "Audio" && book.readProgress <= 0f
                "Copia en Lee.pe" -> book.offlineCopyUri != null
                "En curso" -> book.format != "Audio" && book.readProgress > 0f && book.readProgress < 1f
                "Terminados" -> book.format != "Audio" && book.readProgress >= 1f
                "Favoritos" -> book.isFavorite
                "EPUB" -> book.format == "EPUB"
                "PDF" -> book.format == "PDF"
                "Audio" -> book.format == "Audio"
                else -> true
            }
            val matchesCollection = selectedCollection == null || book.collectionId == selectedCollection
            matchesQuery && matchesFilter && matchesCollection
        }
    }
    val continueBooks = remember(books) {
        books.asSequence()
            .filter { it.format != "Audio" && it.readProgress > 0f && it.readProgress < 1f }
            .sortedByDescending { it.lastOpenedAt ?: 0L }
            .take(8)
            .toList()
    }
    val orderedBooks = remember(visibleBooks, sortMode) {
        when (sortMode) {
            "Última lectura" -> visibleBooks.sortedByDescending { it.lastOpenedAt ?: 0L }
            "Fecha de importación" -> visibleBooks.sortedByDescending { it.importedAt }
            "Progreso" -> visibleBooks.sortedByDescending { it.readProgress }
            "Título Z–A" -> visibleBooks.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            else -> visibleBooks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "dashboard") {
            LibraryDashboardHeader(
                totalBooks = books.size,
                busy = scannerBusy || scannerImporting,
                onImport = {
                    documentPicker.launch(arrayOf("application/epub+zip", "application/pdf", "audio/*"))
                },
                onScan = { directoryScannerLauncher.launch(null) },
                onStats = { showLibraryStats = true },
                onBackup = { showBackupOptions = true }
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = "Buscar libros por título, formato o colección"
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text("Buscar título, formato o colección", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                )
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Borrar búsqueda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (continueBooks.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Retomar lectura", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Sigue justo donde lo dejaste",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                        Text("${continueBooks.size}", color = MaterialTheme.colorScheme.secondary)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueBooks, key = { it.uri.toString() }) { book ->
                            ContinueReadingCard(book = book, wide = continueBooks.size == 1, onOpen = { openBook(context, book) })
                        }
                    }
                }
            }
        }

        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters, key = { it }) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }
        }

        if (collections.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Colección", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { collectionMenuExpanded = true }) {
                            Text(selectedCollection ?: "Todas")
                        }
                        DropdownMenu(
                            expanded = collectionMenuExpanded,
                            onDismissRequest = { collectionMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas las colecciones") },
                                onClick = {
                                    selectedCollection = null
                                    collectionMenuExpanded = false
                                }
                            )
                            collections.forEach { collection ->
                                DropdownMenuItem(
                                    text = { Text(collection) },
                                    onClick = {
                                        selectedCollection = collection
                                        collectionMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "viewControls") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryDisplayToggle(
                    listMode = listMode,
                    onModeChange = { listMode = it }
                )
                Box {
                    TextButton(onClick = { sortMenuExpanded = true }) {
                        Text("Orden: $sortMode")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        listOf("Título A–Z", "Título Z–A", "Última lectura", "Fecha de importación", "Progreso").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    sortMode = option
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("En este dispositivo", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${books.size} archivo${if (books.size == 1) "" else "s"} · Mostrando ${orderedBooks.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Text(
                    if (listMode) "Lista" else "Portadas",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (visibleBooks.isEmpty()) {
            item {
                EmptyLibraryCard(
                    hasBooks = books.isNotEmpty(),
                    onImport = { documentPicker.launch(arrayOf("application/epub+zip", "application/pdf", "audio/*")) }
                )
            }
        } else {
            items(
                count = if (listMode) orderedBooks.size else (orderedBooks.size + 1) / 2,
                key = { row -> orderedBooks[if (listMode) row else row * 2].uri.toString() }
            ) { rowIndex ->
                val first = if (listMode) rowIndex else rowIndex * 2
                val rowBooks = orderedBooks.subList(
                    first, minOf(first + if (listMode) 1 else 2, orderedBooks.size)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowBooks.forEach { book ->
                        BookCard(
                            book = book,
                            savingBookUri = savingBookUri,
                            modifier = Modifier.weight(1f),
                            compact = listMode,
                            onOpen = { openBook(context, book) },
                            onRemove = { pendingRemoval = book },
                            onSaveOfflineCopy = {
                                val key = book.uri.toString()
                                if (savingBookUri != key) {
                                    savingBookUri = key
                                    coroutineScope.launch {
                                        try {
                                            val displayName = withContext(Dispatchers.IO) { queryDisplayName(context, book.uri) }
                                            OfflineBookStore.saveCopy(context, book.uri, displayName)
                                            offlineRevision++
                                            Toast.makeText(context, "Copia guardada para usar sin conexión", Toast.LENGTH_SHORT).show()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            Toast.makeText(context, error.message ?: "No se pudo guardar la copia", Toast.LENGTH_LONG).show()
                                        } finally {
                                            savingBookUri = null
                                        }
                                    }
                                }
                            },
                            onRemoveOfflineCopy = {
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) { OfflineBookStore.deleteCopy(context, book.uri) }
                                    offlineRevision++
                                    Toast.makeText(context, "Copia sin conexión eliminada", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onExportAnnotations = {
                                coroutineScope.launch {
                                    try {
                                        val annotations = annotationDao.getByBook(book.uri.toString())
                                        if (annotations.isEmpty()) {
                                            Toast.makeText(context, "Este libro todavía no tiene anotaciones", Toast.LENGTH_SHORT).show()
                                        } else {
                                            pendingMarkdown = AnnotationMarkdownExporter.toMarkdown(
                                                bookTitle = book.title,
                                                format = book.format,
                                                annotations = annotations
                                            )
                                            exportAnnotationsLauncher.launch(annotationExportFileName(book.title))
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        Toast.makeText(context, error.message ?: "No se pudieron leer las anotaciones", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onToggleFavorite = {
                                coroutineScope.launch {
                                    dao.updateFavorite(book.uri.toString(), !book.isFavorite)
                                }
                            },
                            onEditTitle = {
                                editingBook = book
                                titleDraft = book.title
                            },
                            onOrganize = {
                                organizingBook = book
                                collectionDraft = book.collectionId.orEmpty()
                            }
                        )
                    }
                    if (!listMode && rowBooks.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        item {
            Text(
                "La biblioteca, la búsqueda y las anotaciones funcionan en tu teléfono, sin cuenta ni servidores.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(book: LocalBook, wide: Boolean, onOpen: () -> Unit) {
    val progress = book.readProgress.coerceIn(0f, 1f)
    val cardWidth = if (wide) Modifier.fillMaxWidth() else Modifier.width(232.dp)
    Card(
        modifier = cardWidth.clickable(onClickLabel = "Continuar ${book.title}", onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (wide) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 76.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF8547F5), Color(0xFF16A8D8))),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (book.format) {
                        "PDF" -> LocalPdfCover(
                            originalUri = book.uri,
                            offlineCopyUri = book.offlineCopyUri,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                        "EPUB" -> LocalEpubCover(
                            originalUri = book.uri,
                            offlineCopyUri = book.offlineCopyUri,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        )
                        else -> Icon(
                            Icons.Default.MenuBook, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${(progress * 100).toInt()}% leído",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        book.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Continuar leyendo  →",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF8547F5), Color(0xFF16A8D8))),
                                RoundedCornerShape(11.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (book.format) {
                            "PDF" -> LocalPdfCover(
                                originalUri = book.uri,
                                offlineCopyUri = book.offlineCopyUri,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp))
                            )
                            "EPUB" -> LocalEpubCover(
                                originalUri = book.uri,
                                offlineCopyUri = book.offlineCopyUri,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp))
                            )
                            else -> Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${(progress * 100).toInt()}% leído",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    book.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Continuar  →",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LibraryStatisticRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyLibraryCard(hasBooks: Boolean, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(Color(0xFF211744), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = Color(0xFFC5A9FF),
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                if (hasBooks) "No encontramos coincidencias" else "Tu próxima historia empieza aquí",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (hasBooks) "Prueba otro título o cambia el filtro."
                else "Importa un EPUB, PDF o audiolibro desde tu dispositivo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasBooks) {
                Button(onClick = onImport, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Elegir archivos")
                }
            }
        }
    }
}

@Composable
private fun BookCard(
    book: LocalBook,
    savingBookUri: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onSaveOfflineCopy: () -> Unit,
    onRemoveOfflineCopy: () -> Unit,
    onExportAnnotations: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTitle: () -> Unit,
    onOrganize: () -> Unit
) {
    val (icon, colors) = when (book.format) {
        "PDF" -> Icons.Default.PictureAsPdf to listOf(Color(0xFF5C2A70), Color(0xFF1F4C70))
        "Audio" -> Icons.Default.Headphones to listOf(Color(0xFF145D67), Color(0xFF26396F))
        else -> Icons.Default.MenuBook to listOf(Color(0xFF603680), Color(0xFF204D74))
    }

    Card(
        modifier = modifier.clickable(onClickLabel = "Abrir ${book.title}", onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 86.dp)
                        .background(Brush.linearGradient(colors), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when (book.format) {
                        "PDF" -> LocalPdfCover(book.uri, book.offlineCopyUri, Modifier.fillMaxSize())
                        "EPUB" -> LocalEpubCover(book.uri, book.offlineCopyUri, Modifier.fillMaxSize())
                        else -> Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        book.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            book.format == "Audio" -> "Audiolibro"
                            book.readProgress >= 1f -> "Terminado"
                            book.readProgress > 0f -> "${(book.readProgress * 100).toInt()}% leído"
                            else -> "Por leer"
                        } + " · ${book.format}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (book.format != "Audio" && book.readProgress > 0f) {
                        LinearProgressIndicator(
                            progress = book.readProgress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                BookActions(
                    book = book,
                    savingBookUri = savingBookUri,
                    onRemove = onRemove,
                    onSaveOfflineCopy = onSaveOfflineCopy,
                    onRemoveOfflineCopy = onRemoveOfflineCopy,
                    onExportAnnotations = onExportAnnotations,
                    onToggleFavorite = onToggleFavorite,
                    onEditTitle = onEditTitle,
                    onOrganize = onOrganize
                )
            }
        } else {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.linearGradient(colors), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (book.format) {
                    "PDF" -> LocalPdfCover(
                        originalUri = book.uri,
                        offlineCopyUri = book.offlineCopyUri,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )
                    "EPUB" -> LocalEpubCover(
                        originalUri = book.uri,
                        offlineCopyUri = book.offlineCopyUri,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )
                    else -> Icon(
                        icon, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(42.dp)
                    )
                }
                if (book.isFavorite) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Favorito",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    )
                }
                BookActions(
                    book = book,
                    savingBookUri = savingBookUri,
                    modifier = Modifier.align(Alignment.TopEnd),
                    iconTint = Color.White,
                    onRemove = onRemove,
                    onSaveOfflineCopy = onSaveOfflineCopy,
                    onRemoveOfflineCopy = onRemoveOfflineCopy,
                    onExportAnnotations = onExportAnnotations,
                    onToggleFavorite = onToggleFavorite,
                    onEditTitle = onEditTitle,
                    onOrganize = onOrganize
                )
                Text(
                    book.format,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .background(Color(0x55000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                book.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.format != "Audio" && book.readProgress > 0f) {
                LinearProgressIndicator(
                    progress = book.readProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            val readingStatus = when {
                book.format == "Audio" -> "Audiolibro"
                book.readProgress >= 1f -> "Terminado"
                book.readProgress > 0f -> "${(book.readProgress.coerceIn(0f, 1f) * 100).toInt()}% leído"
                else -> "Sin empezar"
            }
            Text(
                readingStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = if (book.offlineCopyUri == null) 4.dp else 2.dp)
            )
            if (book.offlineCopyUri != null) {
                Text(
                    "Disponible sin conexión",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun BookActions(
    book: LocalBook,
    savingBookUri: String?,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onRemove: () -> Unit,
    onSaveOfflineCopy: () -> Unit,
    onRemoveOfflineCopy: () -> Unit,
    onExportAnnotations: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTitle: () -> Unit,
    onOrganize: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${book.title}", tint = iconTint)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (book.isFavorite) "Quitar de favoritos" else "Agregar a favoritos") },
                            onClick = {
                                expanded = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (book.offlineCopyUri != null) "Quitar copia sin conexión" else if (savingBookUri == book.uri.toString()) "Guardando copia…" else "Guardar sin conexión")
                            },
                            enabled = savingBookUri != book.uri.toString(),
                            onClick = {
                                expanded = false
                                if (book.offlineCopyUri != null) onRemoveOfflineCopy() else onSaveOfflineCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar anotaciones a Markdown") },
                            onClick = {
                                expanded = false
                                onExportAnnotations()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Organizar en colección") },
                            onClick = {
                                expanded = false
                                onOrganize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar título") },
                            onClick = {
                                expanded = false
                                onEditTitle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quitar de la biblioteca") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onRemove()
                            }
                        )
                    }
                }

}

private fun queryDisplayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameColumn >= 0) cursor.getString(nameColumn) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "Libro importado"

private fun formatFromFileName(fileName: String): String? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when {
        extension == "epub" -> "EPUB"
        extension == "pdf" -> "PDF"
        extension in SUPPORTED_AUDIO_EXTENSIONS -> "Audio"
        else -> null
    }
}

private fun openBook(context: Context, book: LocalBook) {
    if (book.format == "Audio") {
        context.startActivity(
            Intent(context, AudioPlayerActivity::class.java)
                .setData(book.uri)
                .putExtra(AudioPlayerActivity.EXTRA_TITLE, book.title)
                .putExtra(OfflineBookStore.EXTRA_COPY_URI, book.offlineCopyUri?.toString())
        )
        return
    }
    val activity = if (book.format == "PDF") PdfReaderActivity::class.java else ReaderActivity::class.java
    val titleExtra = if (book.format == "PDF") PdfReaderActivity.EXTRA_TITLE else ReaderActivity.EXTRA_TITLE
    context.startActivity(
        Intent(context, activity)
            .setData(book.uri)
            .putExtra(titleExtra, book.title)
            .putExtra(OfflineBookStore.EXTRA_COPY_URI, book.offlineCopyUri?.toString())
    )
}

private suspend fun migrateLegacyLibrary(context: Context, dao: BookDao) {
    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (preferences.getBoolean(KEY_LEGACY_MIGRATED, false)) return

    val legacyUris = preferences.getStringSet(KEY_DOCUMENT_URIS, emptySet()).orEmpty().toSet()
    if (legacyUris.isEmpty()) {
        preferences.edit()
            .putBoolean(KEY_LEGACY_MIGRATED, true)
            .remove(KEY_DOCUMENT_URIS)
            .apply()
        return
    }

    val (books, pendingUris) = withContext(Dispatchers.IO) {
        val books = mutableListOf<BookEntity>()
        val pendingUris = mutableSetOf<String>()
        val persistedReadUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

        legacyUris.forEach { rawUri ->
            val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
            if (uri == null || rawUri !in persistedReadUris) {
                pendingUris += rawUri
                return@forEach
            }

            val fileName = runCatching { queryDisplayName(context, uri) }.getOrNull()
            val format = fileName?.let(::formatFromFileName)
            if (fileName == null || format == null) {
                pendingUris += rawUri
                return@forEach
            }

            books += BookEntity(
                uri = uri.toString(),
                title = fileName.substringBeforeLast('.', fileName),
                format = format,
                importedAt = System.currentTimeMillis()
            )
        }
        books to pendingUris
    }

    dao.insertAll(books)

    val editor = preferences.edit()
    if (pendingUris.isEmpty()) {
        editor.putBoolean(KEY_LEGACY_MIGRATED, true)
            .remove(KEY_DOCUMENT_URIS)
    } else {
        editor.putStringSet(KEY_DOCUMENT_URIS, pendingUris)
    }
    editor.apply()
}
