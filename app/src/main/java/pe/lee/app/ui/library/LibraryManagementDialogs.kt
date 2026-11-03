package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import pe.lee.app.data.local.CollectionEntity
import pe.lee.app.data.repository.SettingsRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope



@Composable
internal fun LibraryManagementDialogs(
    context: Context,
    viewModel: LibraryViewModel,
    settings: SettingsRepository,
    coroutineScope: CoroutineScope,
    pendingRemoval: MutableState<LocalBook?>,
    organizingBook: MutableState<LocalBook?>,
    collections: List<String>,
    collectionDraft: MutableState<String>,
    showCollectionsManager: MutableState<Boolean>,
    storedCollections: List<CollectionEntity>,
    collectionBeingRenamed: MutableState<CollectionEntity?>,
    collectionRenameDraft: MutableState<String>,
    editingBook: MutableState<LocalBook?>,
    titleDraft: MutableState<String>,
    selectedCollection: MutableState<String?>,
    onRelinkFile: (LocalBook) -> Unit,
    onOfflineBooksChanged: () -> Unit
) {
    pendingRemoval.value?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRemoval.value = null },
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
                            val uri = book.uri.toString()
                            viewModel.deleteBook(uri)
                            val cleanupComplete = withContext(Dispatchers.IO) {
                                DeletedBookCleanup.complete(context, uri, settings, viewModel::completeCleanup)
                            }
                            pendingRemoval.value = null
                            onOfflineBooksChanged()
                            Toast.makeText(
                                context,
                                if (cleanupComplete) "Libro quitado de la biblioteca"
                                else "Libro quitado. La limpieza restante se reintentará al abrir la biblioteca.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval.value = null }) { Text("Cancelar") }
            }
        )
    }

    organizingBook.value?.let { book ->
        AlertDialog(
            onDismissRequest = { organizingBook.value = null },
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
                                            viewModel.updateCollection(book.uri.toString(), collection)
                                            organizingBook.value = null
                                        }
                                    },
                                    label = { Text(collection) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = collectionDraft.value,
                        onValueChange = { collectionDraft.value = it },
                        label = { Text("Crear o usar colección") },
                        placeholder = { Text("Ej. Fantasía, Por leer") },
                        singleLine = true
                    )
                    TextButton(onClick = { onRelinkFile(book) }) {
                        Text("Volver a vincular archivo")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = collectionDraft.value.isNotBlank(),
                    onClick = {
                        val name = collectionDraft.value.trim()
                        coroutineScope.launch {
                            runCatching { viewModel.updateCollection(book.uri.toString(), name) }
                                .onSuccess {
                                    organizingBook.value = null
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
                                    viewModel.updateCollection(book.uri.toString(), null)
                                    organizingBook.value = null
                                }
                            }
                        ) { Text("Quitar") }
                    }
                    TextButton(onClick = { organizingBook.value = null }) { Text("Cancelar") }
                }
            }
        )
    }

    if (showCollectionsManager.value) {
        AlertDialog(
            onDismissRequest = { showCollectionsManager.value = false },
            title = { Text("Administrar colecciones") },
            text = {
                if (storedCollections.isEmpty()) {
                    Text("Todavía no hay colecciones. Crea una al organizar un libro.")
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        storedCollections.forEachIndexed { index, collection ->
                            Column {
                                Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        enabled = index > 0,
                                        onClick = { coroutineScope.launch { viewModel.moveCollection(collection.id, -1) } }
                                    ) { Text("Subir") }
                                    TextButton(
                                        enabled = index < storedCollections.lastIndex,
                                        onClick = { coroutineScope.launch { viewModel.moveCollection(collection.id, 1) } }
                                    ) { Text("Bajar") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            collectionBeingRenamed.value = collection
                                            collectionRenameDraft.value = collection.name
                                        }
                                    ) { Text("Renombrar") }
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                viewModel.deleteCollection(collection.id)
                                                if (selectedCollection.value == collection.name) selectedCollection.value = null
                                            }
                                        }
                                    ) { Text("Eliminar") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCollectionsManager.value = false }) { Text("Listo") }
            }
        )
    }

    collectionBeingRenamed.value?.let { collection ->
        AlertDialog(
            onDismissRequest = { collectionBeingRenamed.value = null },
            title = { Text("Renombrar colección") },
            text = {
                OutlinedTextField(
                    value = collectionRenameDraft.value,
                    onValueChange = { collectionRenameDraft.value = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = collectionRenameDraft.value.isNotBlank(),
                    onClick = {
                        val newName = collectionRenameDraft.value.trim()
                        coroutineScope.launch {
                            runCatching { viewModel.renameCollection(collection.id, newName) }
                                .onSuccess {
                                    if (selectedCollection.value == collection.name) selectedCollection.value = newName
                                    collectionBeingRenamed.value = null
                                }
                                .onFailure {
                                    Toast.makeText(context, it.message ?: "No se pudo renombrar la colección", Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { collectionBeingRenamed.value = null }) { Text("Cancelar") }
            }
        )
    }

    editingBook.value?.let { book ->
        AlertDialog(
            onDismissRequest = { editingBook.value = null },
            title = { Text("Editar título") },
            text = {
                OutlinedTextField(
                    value = titleDraft.value,
                    onValueChange = { titleDraft.value = it },
                    label = { Text("Título del libro") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = titleDraft.value.isNotBlank(),
                    onClick = {
                        coroutineScope.launch {
                            runCatching { viewModel.updateTitle(book.uri.toString(), titleDraft.value.trim()) }
                                .onSuccess {
                                    editingBook.value = null
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
                TextButton(onClick = { editingBook.value = null }) { Text("Cancelar") }
            }
        )
    }

}
