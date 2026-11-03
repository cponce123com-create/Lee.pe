package pe.lee.app.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import pe.lee.app.data.repository.AudioProgressStore
import pe.lee.app.data.repository.LocalBackupRepository
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.OfflineBookStore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope
import pe.lee.app.data.repository.SettingsRepository



internal data class LibraryDocumentActions(
    val importDocuments: () -> Unit,
    val scanDirectory: () -> Unit,
    val relinkBook: (LocalBook) -> Unit,
    val exportAnnotationsFile: (String) -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit
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
    organizingBook: MutableState<LocalBook?>,
    onOfflineBooksChanged: () -> Unit
): LibraryDocumentActions {
    val exportAnnotationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val markdown = pendingMarkdown.value
        pendingMarkdown.value = null
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
            backupBusy.value = true
            try {
                LocalLibraryBackup.save(context, destination, backupRepository)
                Toast.makeText(context, "Respaldo local exportado correctamente", Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "No se pudo exportar el respaldo", Toast.LENGTH_LONG).show()
            } finally {
                backupBusy.value = false
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) coroutineScope.launch {
            backupBusy.value = true
            try {
                backupPreview.value = LocalLibraryBackup.preview(context, picked, backupRepository)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Respaldo no válido", Toast.LENGTH_LONG).show()
            } finally {
                backupBusy.value = false
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
                    viewModel.insertBooks(newBooks)
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

    val relinkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        val targetBook = organizingBook.value
        if (pickedUri != null && targetBook != null) coroutineScope.launch {
            try {
                val displayName = withContext(Dispatchers.IO) { queryDisplayName(context, pickedUri) }
                val pickedFormat = formatFromFileName(displayName)
                require(pickedFormat == targetBook.format) {
                    "Selecciona un archivo ${targetBook.format} para volver a vincular este libro."
                }
                context.contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                context.contentResolver.openInputStream(pickedUri)?.use { input ->
                    require(input.read() >= 0) { "El archivo seleccionado está vacío." }
                } ?: error("No se pudo leer el archivo. Revisa el permiso del proveedor.")

                val rebound = viewModel.rebindUri(targetBook.id, pickedUri.toString())
                require(rebound) { "Ese archivo ya está vinculado a otro libro de la biblioteca." }
                if (targetBook.uri != pickedUri) {
                    rebindBookScopedPreferences(context, targetBook.uri.toString(), pickedUri.toString())
                    val audioPosition = AudioProgressStore.get(context, targetBook.uri.toString())
                    if (audioPosition > 0L) AudioProgressStore.put(context, pickedUri.toString(), audioPosition)
                    AudioProgressStore.remove(context, targetBook.uri.toString())
                    withContext(Dispatchers.IO) {
                        OfflineBookStore.rebindCopy(context, targetBook.uri, pickedUri)
                    }
                }
                organizingBook.value = null
                onOfflineBooksChanged()
                Toast.makeText(context, "Libro revinculado; se conservaron su progreso y anotaciones", Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "No se pudo revincular el libro", Toast.LENGTH_LONG).show()
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
                }
            )
        },
        exportAnnotationsFile = { exportAnnotationsLauncher.launch(it) },
        exportBackup = { backupExportLauncher.launch("Lee.pe-respaldo-${readingDayKey(System.currentTimeMillis())}.json") },
        importBackup = { backupImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    )
}
