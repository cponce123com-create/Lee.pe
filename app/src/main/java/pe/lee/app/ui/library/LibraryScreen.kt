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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    LaunchedEffect(context, storedBooks, offlineRevision) {
        offlineCopyUris = withContext(Dispatchers.IO) {
            storedBooks.mapNotNull { book ->
                OfflineBookStore.findCopy(context, Uri.parse(book.uri))?.let { book.uri to Uri.fromFile(it) }
            }.toMap()
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

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val permissionPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            if (!permissionPersisted) {
                Toast.makeText(
                    context,
                    "Este proveedor no permite conservar el acceso al archivo. Elige otro proveedor o guarda una copia en el dispositivo.",
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }

            val fileName = queryDisplayName(context, uri)
            if (formatFromFileName(fileName) == null) {
                Toast.makeText(context, "Formato no compatible todavía", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            val format = formatFromFileName(fileName) ?: return@rememberLauncherForActivityResult
            coroutineScope.launch {
                if (dao.findByUri(uri.toString()) != null) {
                    Toast.makeText(context, "Este archivo ya está en tu biblioteca", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                runCatching {
                    dao.insert(
                        BookEntity(
                            uri = uri.toString(),
                            title = fileName.substringBeforeLast('.', fileName),
                            format = format,
                            importedAt = System.currentTimeMillis()
                        )
                    )
                }.onSuccess {
                    Toast.makeText(context, "Añadido a tu biblioteca local", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "No se pudo guardar el archivo en la biblioteca", Toast.LENGTH_LONG).show()
                }
            }
        }
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

    val filters = listOf("Todos", "En curso", "Terminados", "Favoritos", "EPUB", "PDF", "Audio")
    val visibleBooks = books.filter { book ->
        val matchesQuery = book.title.contains(query, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
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
    val continueBooks = books
        .filter { it.format != "Audio" && it.readProgress > 0f && it.readProgress < 1f }
        .sortedByDescending { it.lastOpenedAt ?: 0L }
        .take(8)
    val orderedBooks = when (sortMode) {
        "Última lectura" -> visibleBooks.sortedByDescending { it.lastOpenedAt ?: 0L }
        "Fecha de importación" -> visibleBooks.sortedByDescending { it.importedAt }
        else -> visibleBooks.sortedBy { it.title.lowercase() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF8D50FF), Color(0xFF27C9FF))
                            ),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(27.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lee.pe", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Lee · Escucha · Disfruta",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { showLibraryStats = true }) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Estadísticas de la biblioteca",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tu biblioteca", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Tus historias, disponibles en el dispositivo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text("Buscar título", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                )
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

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { filter ->
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box {
                    TextButton(onClick = { sortMenuExpanded = true }) {
                        Text("Orden: $sortMode")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        listOf("Título A–Z", "Última lectura", "Fecha de importación").forEach { option ->
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
                        "${books.size} archivo${if (books.size == 1) "" else "s"} guardado${if (books.size == 1) "" else "s"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = { documentPicker.launch(arrayOf("application/epub+zip", "application/pdf", "audio/*")) },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = ButtonDefaults.ContentPadding
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importar")
                }
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
            items(orderedBooks.chunked(2)) { rowBooks ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowBooks.forEach { book ->
                        BookCard(
                            book = book,
                            savingBookUri = savingBookUri,
                            modifier = Modifier.weight(1f),
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
                    if (rowBooks.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        item {
            Text(
                "La biblioteca se guarda en tu teléfono. La lectura y la sincronización se incorporarán por etapas.",
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
        modifier = cardWidth.clickable(onClick = onOpen),
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
                    Icon(
                        if (book.format == "PDF") Icons.Default.PictureAsPdf else Icons.Default.MenuBook,
                        contentDescription = book.format,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
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
                        Icon(
                            if (book.format == "PDF") Icons.Default.PictureAsPdf else Icons.Default.MenuBook,
                            contentDescription = book.format,
                            tint = Color.White
                        )
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
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onSaveOfflineCopy: () -> Unit,
    onRemoveOfflineCopy: () -> Unit,
    onExportAnnotations: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTitle: () -> Unit,
    onOrganize: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val (icon, colors) = when (book.format) {
        "PDF" -> Icons.Default.PictureAsPdf to listOf(Color(0xFF5C2A70), Color(0xFF1F4C70))
        "Audio" -> Icons.Default.Headphones to listOf(Color(0xFF145D67), Color(0xFF26396F))
        else -> Icons.Default.MenuBook to listOf(Color(0xFF603680), Color(0xFF204D74))
    }

    Card(
        modifier = modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.linearGradient(colors), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = book.format, tint = Color.White, modifier = Modifier.size(42.dp))
                if (book.isFavorite) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Favorito",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones del libro", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (book.isFavorite) "Quitar de favoritos" else "Agregar a favoritos") },
                            onClick = {
                                menuExpanded = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (book.offlineCopyUri != null) "Quitar copia sin conexión" else if (savingBookUri == book.uri.toString()) "Guardando copia…" else "Guardar sin conexión")
                            },
                            enabled = savingBookUri != book.uri.toString(),
                            onClick = {
                                menuExpanded = false
                                if (book.offlineCopyUri != null) onRemoveOfflineCopy() else onSaveOfflineCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar anotaciones a Markdown") },
                            onClick = {
                                menuExpanded = false
                                onExportAnnotations()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Organizar en colección") },
                            onClick = {
                                menuExpanded = false
                                onOrganize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar título") },
                            onClick = {
                                menuExpanded = false
                                onEditTitle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quitar de la biblioteca") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            }
                        )
                    }
                }
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
