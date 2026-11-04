package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.repository.LocalBackupRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.feature.library.R
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
    backupRepository: LocalBackupRepository,
    coroutineScope: CoroutineScope,
    storedBooks: List<BookEntity>,
    scanner: ScannerUiState,
    onCancelScan: () -> Unit,
    onClearScanPreview: () -> Unit,
    showBackupOptions: MutableState<Boolean>,
    backupBusy: MutableState<Boolean>,
    backupPreview: MutableState<LocalBackupPreview?>,
    backupPasswordUri: MutableState<Uri?>,
    onExportBackup: (String) -> Unit,
    onImportBackup: () -> Unit,
    onUnlockBackup: (Uri, String) -> Unit
) {
    var importingScan by remember { mutableStateOf(false) }
    var showExportPassword by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var confirmExportPassword by remember { mutableStateOf("") }

    if (scanner.busy) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.scanner_searching_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(scanner.folderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.scanner_folders_count, scanner.folders, scanner.folders))
                    Text(pluralStringResource(R.plurals.scanner_files_count, scanner.files, scanner.files))
                    Text(pluralStringResource(R.plurals.scanner_books_count, scanner.books, scanner.books))
                    Text(stringResource(R.string.scanner_privacy_notice))
                }
            },
            confirmButton = {
                TextButton(onClick = onCancelScan) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    scanner.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!importingScan) onClearScanPreview() },
            title = { Text(stringResource(R.string.scanner_result_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pluralStringResource(R.plurals.scanner_new_books_count, preview.books.size, preview.books.size))
                    Text(pluralStringResource(R.plurals.scanner_duplicates_count, preview.duplicates, preview.duplicates))
                    Text(pluralStringResource(R.plurals.scanner_visited_files_count, preview.visitedFiles, preview.visitedFiles))
                    Text(pluralStringResource(R.plurals.scanner_visited_folders_count, preview.visitedFolders, preview.visitedFolders))
                    if (preview.skippedFolders > 0) {
                        Text(pluralStringResource(R.plurals.scanner_skipped_folders_count, preview.skippedFolders, preview.skippedFolders))
                    }
                    if (preview.limitReached) {
                        Text(
                            stringResource(R.string.scanner_limit_reached),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        stringResource(R.string.scanner_import_privacy_notice)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !importingScan && preview.books.isNotEmpty(),
                    onClick = {
                        importingScan = true
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
                                    context.resources.getQuantityString(R.plurals.scanner_imported_books_toast, entries.size, entries.size),
                                    Toast.LENGTH_LONG
                                ).show()
                                onClearScanPreview()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Toast.makeText(
                                    context, context.getString(R.string.scanner_import_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                importingScan = false
                            }
                        }
                    }
                ) { Text(stringResource(if (importingScan) R.string.action_importing else R.string.scanner_add_to_library)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !importingScan,
                    onClick = onClearScanPreview
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showBackupOptions.value) {
        AlertDialog(
            onDismissRequest = { if (!backupBusy.value) showBackupOptions.value = false },
            title = { Text(stringResource(R.string.backup_local_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.backup_description))
                    Text(
                        stringResource(R.string.backup_books_not_included),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.backup_privacy_notice),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !backupBusy.value, onClick = {
                    showBackupOptions.value = false
                    showExportPassword = true
                }) { Text(stringResource(R.string.backup_export)) }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !backupBusy.value, onClick = {
                        showBackupOptions.value = false
                        onImportBackup()
                    }) { Text(stringResource(R.string.backup_restore)) }
                    TextButton(onClick = { showBackupOptions.value = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        )
    }

    if (showExportPassword) {
        AlertDialog(
            onDismissRequest = {
                if (!backupBusy.value) {
                    showExportPassword = false
                    exportPassword = ""
                    confirmExportPassword = ""
                }
            },
            title = { Text(stringResource(R.string.backup_password_export_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_password_guidance))
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmExportPassword,
                        onValueChange = { confirmExportPassword = it },
                        label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                    if (confirmExportPassword.isNotEmpty() && exportPassword != confirmExportPassword) {
                        Text(stringResource(R.string.backup_password_mismatch), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !backupBusy.value && exportPassword.length >= 8 && exportPassword == confirmExportPassword,
                    onClick = {
                        onExportBackup(exportPassword)
                        exportPassword = ""
                        confirmExportPassword = ""
                        showExportPassword = false
                    }
                ) { Text(stringResource(R.string.backup_export)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    exportPassword = ""
                    confirmExportPassword = ""
                    showExportPassword = false
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    backupPasswordUri.value?.let { lockedUri ->
        var importPassword by remember(lockedUri) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!backupBusy.value) backupPasswordUri.value = null },
            title = { Text(stringResource(R.string.backup_password_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_password_guidance))
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !backupBusy.value && importPassword.length >= 8,
                    onClick = { onUnlockBackup(lockedUri, importPassword) }
                ) { Text(stringResource(if (backupBusy.value) R.string.backup_restoring else R.string.backup_unlock)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !backupBusy.value,
                    onClick = { backupPasswordUri.value = null }
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    backupPreview.value?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!backupBusy.value) backupPreview.value = null },
            title = { Text(stringResource(R.string.backup_confirm_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pluralStringResource(R.plurals.backup_books_count, selected.archive.snapshot.books.size, selected.archive.snapshot.books.size))
                    Text(pluralStringResource(R.plurals.backup_exact_matches_count, selected.exactMatches, selected.exactMatches))
                    Text(pluralStringResource(R.plurals.backup_title_matches_count, selected.titleMatches, selected.titleMatches))
                    Text(pluralStringResource(R.plurals.backup_unavailable_books_count, selected.unavailable, selected.unavailable))
                    Text(pluralStringResource(R.plurals.backup_annotations_count, selected.archive.snapshot.annotations.size, selected.archive.snapshot.annotations.size))
                    Text(
                        stringResource(R.string.backup_restore_safety_notice),
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
                                val report = LocalLibraryBackup.restore(context, selected, backupRepository)
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.backup_restore_result,
                                        context.resources.getQuantityString(R.plurals.backup_books_short, report.linkedBooks, report.linkedBooks),
                                        context.resources.getQuantityString(R.plurals.backup_annotations_short, report.addedAnnotations, report.addedAnnotations),
                                        context.resources.getQuantityString(R.plurals.backup_books_pending_short, report.skippedBooks, report.skippedBooks)
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                                backupPreview.value = null
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.backup_restore_failed), Toast.LENGTH_LONG).show()
                            } finally {
                                backupBusy.value = false
                            }
                        }
                    }
                ) { Text(stringResource(if (backupBusy.value) R.string.backup_restoring else R.string.backup_restore_data)) }
            },
            dismissButton = {
                TextButton(enabled = !backupBusy.value, onClick = { backupPreview.value = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

}
