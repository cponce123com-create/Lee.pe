package pe.lee.app.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.data.repository.AudioProgressStore
import pe.lee.app.data.repository.LocalBackupRepository
import pe.lee.feature.library.R
import pe.lee.feature.notes.R as NotesR

internal data class LibraryDocumentActions(
    val importDocuments: () -> Unit,
    val scanDirectory: () -> Unit,
    val relinkBook: (LocalBook) -> Unit,
    val exportAnnotationsFile: (String) -> Unit,
    val exportBackup: (String) -> Unit,
    val importBackup: () -> Unit,
    val unlockBackup: (Uri, String) -> Unit,
)

@Composable
internal fun rememberLibraryDocumentActions(
    context: Context,
    storedBooks: List<BookEntity>,
    backupRepository: LocalBackupRepository,
    viewModel: LibraryViewModel,
    coroutineScope: CoroutineScope,
    pendingMarkdown: MutableState<String?>,
    backupBusy: MutableState<Boolean>,
    backupPreview: MutableState<LocalBackupPreview?>,
    backupPasswordUri: MutableState<Uri?>,
    organizingBook: MutableState<LocalBook?>,
    onOfflineBooksChanged: () -> Unit,
): LibraryDocumentActions {
    val exportAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val bookId = pendingMarkdown.value
        pendingMarkdown.value = null
        if (uri != null && bookId != null) {
            coroutineScope.launch {
                try {
                    val book = viewModel.findBookById(bookId)
                        ?: throw IOException(context.getString(R.string.library_export_annotations_failed))
                    val annotations = viewModel.annotationsForBook(book.uri)
                    val markdown = AnnotationMarkdownExporter.toMarkdown(
                        bookTitle = book.title,
                        format = book.format,
                        annotations = annotations,
                        pageLabel = { page -> context.getString(NotesR.string.annotation_page_label, page) },
                    )
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IOException(context.getString(R.string.library_export_file_failed))
                        output.bufferedWriter(Charsets.UTF_8).use { it.write(markdown) }
                    }
                    Toast.makeText(context, context.getString(R.string.library_export_annotations_done), Toast.LENGTH_SHORT).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.library_export_annotations_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val pendingBackupPassword = remember { mutableStateOf<String?>(null) }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.leepe.backup"),
    ) { destination ->
        val password = pendingBackupPassword.value
        pendingBackupPassword.value = null
        if (destination != null && password != null) {
            coroutineScope.launch {
                backupBusy.value = true
                try {
                    val adjustedRecords = LocalLibraryBackup.save(context, destination, backupRepository, password)
                    val message = if (adjustedRecords == 0) {
                        context.getString(R.string.library_export_backup_done)
                    } else {
                        context.getString(R.string.library_export_backup_adjusted, adjustedRecords)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(backupErrorMessage(e, R.string.library_export_backup_failed)),
                        Toast.LENGTH_LONG,
                    ).show()
                } finally {
                    backupBusy.value = false
                }
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked ->
        if (picked != null) {
            coroutineScope.launch {
                backupBusy.value = true
                try {
                    backupPreview.value = LocalLibraryBackup.preview(context, picked, backupRepository)
                } catch (_: BackupPasswordRequiredException) {
                    backupPasswordUri.value = picked
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(backupErrorMessage(e, R.string.library_backup_invalid)),
                        Toast.LENGTH_LONG,
                    ).show()
                } finally {
                    backupBusy.value = false
                }
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { selectedUris ->
        if (selectedUris.isNotEmpty()) {
            coroutineScope.launch {
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
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }.isSuccess
                            if (!permitted) {
                                noPermission++
                                return@mapNotNull null
                            }
                            PersistableUriGrants.warnIfNearLimit(context)
                            BookEntity(
                                uri = uri.toString(),
                                title = fileName.substringBeforeLast('.', fileName).ifBlank { fileName },
                                format = format,
                                importedAt = System.currentTimeMillis(),
                            )
                        }
                    }
                    if (newBooks.isNotEmpty()) {
                        viewModel.insertBooks(newBooks)
                    }
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.library_import_summary,
                            newBooks.size,
                            alreadyPresent,
                            unsupported,
                            noPermission,
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.library_import_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val relinkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { pickedUri ->
        val targetBook = organizingBook.value
        if (pickedUri != null && targetBook != null) {
            coroutineScope.launch {
                try {
                    val displayName = withContext(Dispatchers.IO) { queryDisplayName(context, pickedUri) }
                    val pickedFormat = formatFromFileName(displayName)
                    require(pickedFormat == targetBook.format) {
                        context.getString(R.string.library_relink_format, targetBook.format)
                    }
                    context.contentResolver.takePersistableUriPermission(
                        pickedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    PersistableUriGrants.warnIfNearLimit(context)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(pickedUri)?.use { input ->
                            require(input.read() >= 0) {
                                context.getString(R.string.library_relink_empty_file)
                            }
                        } ?: error(context.getString(R.string.library_relink_unreadable))
                    }

                    val rebound = viewModel.rebindUri(targetBook.id, pickedUri.toString())
                    require(rebound) { context.getString(R.string.library_relink_already_linked) }
                    if (targetBook.uri != pickedUri) {
                        PersistableUriGrants.release(context, targetBook.uri)
                        rebindBookScopedPreferences(context, targetBook.uri.toString(), pickedUri.toString())
                        val audioPosition = AudioProgressStore.get(context, targetBook.uri.toString())
                        if (audioPosition > 0L) AudioProgressStore.put(context, pickedUri.toString(), audioPosition)
                        withContext(Dispatchers.IO) {
                            AudioProgressStore.remove(context, targetBook.uri.toString())
                        }
                        withContext(Dispatchers.IO) {
                            OfflineBookStore.rebindCopy(context, targetBook.uri, pickedUri)
                        }
                    }
                    organizingBook.value = null
                    onOfflineBooksChanged()
                    Toast.makeText(
                        context,
                        context.getString(R.string.library_relink_done),
                        Toast.LENGTH_LONG,
                    ).show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.library_relink_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val directoryScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (!granted) {
                Toast.makeText(
                    context,
                    context.getString(R.string.library_folder_permission_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                PersistableUriGrants.warnIfNearLimit(context)
                // The ViewModel owns the scan: it survives rotation and reports
                // progress through its own state (A-36).
                viewModel.scanFolder(context, treeUri, storedBooks.map { it.uri })
            }
        }
    }

    return LibraryDocumentActions(
        importDocuments = { documentPicker.launch(arrayOf("application/epub+zip", "application/pdf", "audio/*")) },
        scanDirectory = { directoryScannerLauncher.launch(null) },
        relinkBook = { book ->
            organizingBook.value = book
            relinkPicker.launch(
                when (book.format) {
                    "EPUB" -> arrayOf("application/epub+zip", "application/octet-stream")
                    "PDF" -> arrayOf("application/pdf")
                    else -> arrayOf("audio/*")
                },
            )
        },
        exportAnnotationsFile = { exportAnnotationsLauncher.launch(it) },
        exportBackup = { password ->
            pendingBackupPassword.value = password
            backupExportLauncher.launch("Lee.pe-respaldo-" + readingDayKey(System.currentTimeMillis()) + ".leepebackup")
        },
        importBackup = {
            backupImportLauncher.launch(
                arrayOf("application/json", "text/plain", "application/octet-stream", "application/vnd.leepe.backup"),
            )
        },
        unlockBackup = { uri, password ->
            coroutineScope.launch {
                backupBusy.value = true
                try {
                    backupPreview.value = LocalLibraryBackup.preview(context, uri, backupRepository, password)
                    backupPasswordUri.value = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(backupErrorMessage(error, R.string.library_backup_invalid)),
                        Toast.LENGTH_LONG,
                    ).show()
                } finally {
                    backupBusy.value = false
                }
            }
        },
    )
}
