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
import pe.lee.feature.library.R
import pe.lee.app.data.repository.SettingsRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    storedBooks: List<LocalBook>,
    collectionBeingRenamed: MutableState<CollectionEntity?>,
    collectionRenameDraft: MutableState<String>,
    editingBook: MutableState<LocalBook?>,
    titleDraft: MutableState<String>,
    selectedCollection: MutableState<String?>,
    onRelinkFile: (LocalBook) -> Unit,
    onOfflineBooksChanged: () -> Unit
) {
    // Deleting a collection also unlinks every book on it; confirm first (A-39).
    val pendingCollectionDelete = remember { mutableStateOf<CollectionEntity?>(null) }

    pendingRemoval.value?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRemoval.value = null },
            title = { Text(stringResource(R.string.library_remove_title)) },
            text = {
                Text(
                    stringResource(
                        if (book.offlineCopyUri != null) R.string.library_remove_book_with_copy_message
                        else R.string.library_remove_book_message,
                        book.title
                    )
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
                                context.getString(
                                    if (cleanupComplete) R.string.library_remove_success
                                    else R.string.library_remove_cleanup_pending
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval.value = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    organizingBook.value?.let { book ->
        AlertDialog(
            onDismissRequest = { organizingBook.value = null },
            title = { Text(stringResource(R.string.library_book_collection_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        book.title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (collections.isNotEmpty()) {
                        Text(stringResource(R.string.library_move_to_collection), fontWeight = FontWeight.SemiBold)
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
                        label = { Text(stringResource(R.string.library_create_or_use_collection)) },
                        placeholder = { Text(stringResource(R.string.library_collection_examples)) },
                        singleLine = true
                    )
                    TextButton(onClick = { onRelinkFile(book) }) {
                        Text(stringResource(R.string.library_relink_file))
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
                                    Toast.makeText(context, context.getString(R.string.library_book_organized, name), Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, context.getString(R.string.library_collection_save_failed), Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                ) { Text(stringResource(R.string.action_save)) }
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
                        ) { Text(stringResource(R.string.action_remove)) }
                    }
                    TextButton(onClick = { organizingBook.value = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        )
    }

    if (showCollectionsManager.value) {
        AlertDialog(
            onDismissRequest = { showCollectionsManager.value = false },
            title = { Text(stringResource(R.string.library_manage_collections)) },
            text = {
                if (storedCollections.isEmpty()) {
                    Text(stringResource(R.string.library_no_collections_yet))
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
                                    ) { Text(stringResource(R.string.action_move_up)) }
                                    TextButton(
                                        enabled = index < storedCollections.lastIndex,
                                        onClick = { coroutineScope.launch { viewModel.moveCollection(collection.id, 1) } }
                                    ) { Text(stringResource(R.string.action_move_down)) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            collectionBeingRenamed.value = collection
                                            collectionRenameDraft.value = collection.name
                                        }
                                    ) { Text(stringResource(R.string.action_rename)) }
                                    TextButton(
                                        onClick = { pendingCollectionDelete.value = collection }
                                    ) { Text(stringResource(R.string.action_delete)) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCollectionsManager.value = false }) { Text(stringResource(R.string.action_done)) }
            }
        )
    }

    pendingCollectionDelete.value?.let { collection ->
        val affected = storedBooks.count { it.collectionId == collection.name }
        AlertDialog(
            onDismissRequest = { pendingCollectionDelete.value = null },
            title = { Text(stringResource(R.string.library_delete_collection_title)) },
            text = {
                Text(
                    if (affected > 0) {
                        stringResource(R.string.library_delete_collection_message, collection.name, affected)
                    } else {
                        stringResource(R.string.library_delete_collection_empty_message, collection.name)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCollectionDelete.value = null
                    coroutineScope.launch {
                        viewModel.deleteCollection(collection.id)
                        if (selectedCollection.value == collection.name) selectedCollection.value = null
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCollectionDelete.value = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    collectionBeingRenamed.value?.let { collection ->
        AlertDialog(
            onDismissRequest = { collectionBeingRenamed.value = null },
            title = { Text(stringResource(R.string.library_rename_collection)) },
            text = {
                OutlinedTextField(
                    value = collectionRenameDraft.value,
                    onValueChange = { collectionRenameDraft.value = it },
                    label = { Text(stringResource(R.string.field_name)) },
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
                                    Toast.makeText(context, it.message ?: context.getString(R.string.library_collection_rename_failed), Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { collectionBeingRenamed.value = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    editingBook.value?.let { book ->
        AlertDialog(
            onDismissRequest = { editingBook.value = null },
            title = { Text(stringResource(R.string.library_edit_title)) },
            text = {
                OutlinedTextField(
                    value = titleDraft.value,
                    onValueChange = { titleDraft.value = it },
                    label = { Text(stringResource(R.string.library_book_title)) },
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
                                    Toast.makeText(context, context.getString(R.string.library_title_updated), Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, context.getString(R.string.library_title_update_failed), Toast.LENGTH_LONG).show()
                                }
                        }
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingBook.value = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

}
