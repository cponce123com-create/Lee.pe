package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import java.io.IOException



@Composable
internal fun LibraryCatalog(
    books: List<LocalBook>,
    visibleBooks: List<LocalBook>,
    continueBooks: List<LocalBook>,
    orderedBooks: List<LocalBook>,
    collections: List<String>,
    query: MutableState<String>,
    selectedFilter: MutableState<LibraryFilter>,
    selectedCollection: MutableState<String?>,
    collectionMenuExpanded: MutableState<Boolean>,
    showCollectionsManager: MutableState<Boolean>,
    sortMenuExpanded: MutableState<Boolean>,
    sortMode: MutableState<LibrarySortMode>,
    listMode: MutableState<Boolean>,
    savingBookUri: MutableState<String?>,
    pendingRemoval: MutableState<LocalBook?>,
    editingBook: MutableState<LocalBook?>,
    titleDraft: MutableState<String>,
    pendingMarkdown: MutableState<String?>,
    organizingBook: MutableState<LocalBook?>,
    collectionDraft: MutableState<String>,
    scannerBusy: Boolean,
    scannerImporting: Boolean,
    context: Context,
    viewModel: LibraryViewModel,
    coroutineScope: CoroutineScope,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onStats: () -> Unit,
    onBackup: () -> Unit,
    onOpen: (LocalBook) -> Unit,
    onOfflineBooksChanged: () -> Unit,
    onExportAnnotationsFile: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "dashboard") {
            LibraryDashboardHeader(
                totalBooks = books.size,
                busy = scannerBusy || scannerImporting,
                onImport = {
                    onImport()
                },
                onScan = { onScan() },
                onStats = onStats,
                onBackup = onBackup
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
                    value = query.value,
                    onValueChange = { query.value = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = "Buscar libros por título, formato o colección"
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (query.value.isEmpty()) {
                            Text("Buscar título, formato o colección", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                )
                if (query.value.isNotBlank()) {
                    IconButton(onClick = { query.value = "" }) {
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
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val singleBook = continueBooks.size == 1
                        val resumeCardWidth = if (singleBook) maxWidth else 232.dp
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(continueBooks, key = { it.uri.toString() }) { book ->
                                ContinueReadingCard(
                                    book = book,
                                    wide = singleBook,
                                    cardWidth = resumeCardWidth,
                                    onOpen = { onOpen(book) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibraryFilter.entries, key = { it.name }) { filter ->
                    FilterChip(
                        selected = selectedFilter.value == filter,
                        onClick = { selectedFilter.value = filter },
                        label = { Text(stringResource(filter.labelRes)) }
                    )
                }
            }
        }

        if (collections.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Colección", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { collectionMenuExpanded.value = true }) {
                            Text(selectedCollection.value ?: "Todas")
                        }
                        DropdownMenu(
                            expanded = collectionMenuExpanded.value,
                            onDismissRequest = { collectionMenuExpanded.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas las colecciones") },
                                onClick = {
                                    selectedCollection.value = null
                                    collectionMenuExpanded.value = false
                                }
                            )
                            collections.forEach { collection ->
                                DropdownMenuItem(
                                    text = { Text(collection) },
                                    onClick = {
                                        selectedCollection.value = collection
                                        collectionMenuExpanded.value = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Administrar colecciones") },
                                onClick = {
                                    showCollectionsManager.value = true
                                    collectionMenuExpanded.value = false
                                }
                            )
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
                    listMode = listMode.value,
                    onModeChange = { listMode.value = it }
                )
                Box {
                    TextButton(onClick = { sortMenuExpanded.value = true }) {
                        Text(stringResource(R.string.library_sort_order, stringResource(sortMode.value.labelRes)))
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded.value,
                        onDismissRequest = { sortMenuExpanded.value = false }
                    ) {
                        LibrarySortMode.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                onClick = {
                                    sortMode.value = option
                                    sortMenuExpanded.value = false
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
                    if (listMode.value) "Lista" else "Portadas",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (visibleBooks.isEmpty()) {
            item {
                EmptyLibraryCard(
                    hasBooks = books.isNotEmpty(),
                    onImport = { onImport() }
                )
            }
        } else {
            items(
                count = if (listMode.value) orderedBooks.size else (orderedBooks.size + 1) / 2,
                key = { row -> orderedBooks[if (listMode.value) row else row * 2].uri.toString() }
            ) { rowIndex ->
                val first = if (listMode.value) rowIndex else rowIndex * 2
                val rowBooks = orderedBooks.subList(
                    first, minOf(first + if (listMode.value) 1 else 2, orderedBooks.size)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowBooks.forEach { book ->
                        BookCard(
                            book = book,
                            savingBookUri = savingBookUri.value,
                            modifier = Modifier.weight(1f),
                            compact = listMode.value,
                            onOpen = { onOpen(book) },
                            onRemove = { pendingRemoval.value = book },
                            onSaveOfflineCopy = {
                                val key = book.uri.toString()
                                if (savingBookUri.value != key) {
                                    savingBookUri.value = key
                                    coroutineScope.launch {
                                        try {
                                            val displayName = withContext(Dispatchers.IO) { queryDisplayName(context, book.uri) }
                                            OfflineBookStore.saveCopy(context, book.uri, displayName)
                                            onOfflineBooksChanged()
                                            Toast.makeText(context, "Copia guardada para usar sin conexión", Toast.LENGTH_SHORT).show()
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            Toast.makeText(context, error.message ?: "No se pudo guardar la copia", Toast.LENGTH_LONG).show()
                                        } finally {
                                            savingBookUri.value = null
                                        }
                                    }
                                }
                            },
                            onRemoveOfflineCopy = {
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) { OfflineBookStore.deleteCopy(context, book.uri) }
                                    onOfflineBooksChanged()
                                    Toast.makeText(context, "Copia sin conexión eliminada", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onExportAnnotations = {
                                coroutineScope.launch {
                                    try {
                                        val annotations = viewModel.annotationsForBook(book.uri.toString())
                                        if (annotations.isEmpty()) {
                                            Toast.makeText(context, "Este libro todavía no tiene anotaciones", Toast.LENGTH_SHORT).show()
                                        } else {
                                            pendingMarkdown.value = AnnotationMarkdownExporter.toMarkdown(
                                                bookTitle = book.title,
                                                format = book.format,
                                                annotations = annotations
                                            )
                                            onExportAnnotationsFile(annotationExportFileName(book.title))
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
                                    viewModel.updateFavorite(book.uri.toString(), !book.isFavorite)
                                }
                            },
                            onEditTitle = {
                                editingBook.value = book
                                titleDraft.value = book.title
                            },
                            onOrganize = {
                                organizingBook.value = book
                                collectionDraft.value = book.collectionId.orEmpty()
                            }
                        )
                    }
                    if (!listMode.value && rowBooks.size == 1) Spacer(Modifier.weight(1f))
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
