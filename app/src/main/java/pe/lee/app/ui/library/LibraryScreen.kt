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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import pe.lee.app.ui.reader.PdfReaderActivity
import pe.lee.app.ui.reader.ReaderActivity
import pe.lee.app.ui.audio.AudioPlayerActivity
import pe.lee.app.data.local.BookDao
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.LeeDatabase
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "lee_pe_library"
private val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "wav", "flac")
private const val KEY_DOCUMENT_URIS = "document_uris"
private const val KEY_LEGACY_MIGRATED = "room_library_migrated"

private data class LocalBook(
    val uri: Uri,
    val title: String,
    val format: String
)

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val dao = remember(context) { LeeDatabase.get(context).bookDao() }
    val storedBooks by dao.observeAll().collectAsState(initial = emptyList())
    val books = remember(storedBooks) {
        storedBooks.map { LocalBook(Uri.parse(it.uri), it.title, it.format) }
    }
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Todos") }

    LaunchedEffect(context, dao) {
        migrateLegacyLibrary(context, dao)
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers do not offer persistable grants.
            }

            val fileName = queryDisplayName(context, uri)
            if (formatFromFileName(fileName) == null) {
                Toast.makeText(context, "Formato no compatible todavía", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            val format = formatFromFileName(fileName) ?: return@rememberLauncherForActivityResult
            coroutineScope.launch {
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

    val filters = listOf("Todos", "EPUB", "PDF", "Audio")
    val visibleBooks = books.filter { book ->
        val matchesQuery = book.title.contains(query, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "EPUB" -> book.format == "EPUB"
            "PDF" -> book.format == "PDF"
            "Audio" -> book.format == "Audio"
            else -> true
        }
        matchesQuery && matchesFilter
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
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
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Biblioteca local",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tu biblioteca", fontSize = 29.sp, fontWeight = FontWeight.Bold)
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

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
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
            items(visibleBooks.chunked(2)) { rowBooks ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowBooks.forEach { book ->
                        BookCard(
                            book = book,
                            modifier = Modifier.weight(1f),
                            onOpen = { openBook(context, book) }
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
private fun BookCard(book: LocalBook, modifier: Modifier = Modifier, onOpen: () -> Unit) {
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
            Text(
                "En tu dispositivo",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = 4.dp)
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
        )
        return
    }
    val activity = if (book.format == "PDF") PdfReaderActivity::class.java else ReaderActivity::class.java
    val titleExtra = if (book.format == "PDF") PdfReaderActivity.EXTRA_TITLE else ReaderActivity.EXTRA_TITLE
    context.startActivity(
        Intent(context, activity)
            .setData(book.uri)
            .putExtra(titleExtra, book.title)
    )
}

private suspend fun migrateLegacyLibrary(context: Context, dao: BookDao) {
    val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (preferences.getBoolean(KEY_LEGACY_MIGRATED, false)) return

    val books = withContext(Dispatchers.IO) {
        preferences.getStringSet(KEY_DOCUMENT_URIS, emptySet())
            .orEmpty()
            .mapNotNull { rawUri ->
                val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return@mapNotNull null
                val fileName = queryDisplayName(context, uri)
                val format = formatFromFileName(fileName) ?: return@mapNotNull null
                BookEntity(
                    uri = uri.toString(),
                    title = fileName.substringBeforeLast('.', fileName),
                    format = format,
                    importedAt = System.currentTimeMillis()
                )
            }
    }

    dao.insertAll(books)
    preferences.edit()
        .putBoolean(KEY_LEGACY_MIGRATED, true)
        .remove(KEY_DOCUMENT_URIS)
        .apply()
}
