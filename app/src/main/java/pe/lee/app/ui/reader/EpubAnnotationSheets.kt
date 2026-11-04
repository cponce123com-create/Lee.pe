package pe.lee.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.app.R
import pe.lee.core.domain.ReaderAnnotation
import androidx.compose.runtime.MutableState



@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EpubAnnotationSheets(
    annotations: List<ReaderAnnotation>,
    showAnnotations: MutableState<Boolean>,
    showNoteEditor: MutableState<Boolean>,
    editingAnnotation: MutableState<ReaderAnnotation?>,
    noteDraft: MutableState<String>,
    onAddBookmark: () -> Unit,
    onAddNote: (String) -> Unit,
    onUpdateAnnotation: (ReaderAnnotation, String) -> Unit,
    onChangeHighlightColor: (ReaderAnnotation, Int) -> Unit,
    onSelectAnnotation: (ReaderAnnotation) -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit
) {
    // Deleting a note used to happen with a single tap next to the row that
    // navigates, and the DAO deletes for good (A-33).
    val pendingDelete = remember { mutableStateOf<ReaderAnnotation?>(null) }
    if (showAnnotations.value) {
        ModalBottomSheet(onDismissRequest = { showAnnotations.value = false }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.annotation_sheet_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = {
                        editingAnnotation.value = null
                        noteDraft.value = ""
                        showNoteEditor.value = true
                    }) { Text(stringResource(R.string.annotation_add_note)) }
                    TextButton(onClick = onAddBookmark) { Text(stringResource(R.string.annotation_save)) }
                }
            }
            if (annotations.isEmpty()) {
                Text(
                    stringResource(R.string.annotation_empty_list),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(annotations, key = { it.id }) { annotation ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).clickable {
                                    onSelectAnnotation(annotation)
                                    showAnnotations.value = false
                                }.padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    annotation.noteText?.takeIf { it.isNotBlank() }
                                        ?: annotation.selectedText?.takeIf { it.isNotBlank() }
                                        ?: if (annotation.kind == "HIGHLIGHT") {
                                            stringResource(
                                                R.string.annotation_highlight_date,
                                                java.text.DateFormat.getDateTimeInstance()
                                                    .format(java.util.Date(annotation.createdAt))
                                            )
                                        } else {
                                            stringResource(
                                                R.string.annotation_bookmark_date,
                                                java.text.DateFormat.getDateTimeInstance()
                                                    .format(java.util.Date(annotation.createdAt))
                                            )
                                        },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (annotation.kind == "HIGHLIGHT") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        listOf(
                                            stringResource(R.string.annotation_color_yellow) to 0xFFFFD54F.toInt(),
                                            stringResource(R.string.annotation_color_blue) to 0xFF64B5F6.toInt(),
                                            stringResource(R.string.annotation_color_green) to 0xFF81C784.toInt(),
                                            stringResource(R.string.annotation_color_pink) to 0xFFF48FB1.toInt()
                                        ).forEach { (label, color) ->
                                            IconButton(
                                                onClick = { onChangeHighlightColor(annotation, color) },
                                                // 34 dp was below the 48 dp minimum touch target (A-19).
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Circle,
                                                    contentDescription = stringResource(
                                                        R.string.annotation_highlight_color, label
                                                    ),
                                                    tint = Color(color),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    stringResource(
                                        when (annotation.kind) {
                                            "NOTE" -> R.string.annotation_open_note
                                            "HIGHLIGHT" -> R.string.annotation_open_highlight
                                            else -> R.string.annotation_open_bookmark
                                        }
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            if (annotation.kind == "NOTE") {
                                IconButton(onClick = {
                                    editingAnnotation.value = annotation
                                    noteDraft.value = annotation.noteText.orEmpty()
                                    showNoteEditor.value = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.annotation_edit_note))
                                }
                            }
                            IconButton(onClick = { pendingDelete.value = annotation }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.annotation_delete_action))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    if (showNoteEditor.value) {
        AlertDialog(
            onDismissRequest = { showNoteEditor.value = false },
            title = {
                Text(
                    stringResource(
                        if (editingAnnotation.value == null) R.string.annotation_new_note
                        else R.string.annotation_edit_note
                    )
                )
            },
            text = {
                OutlinedTextField(
                    value = noteDraft.value,
                    onValueChange = { noteDraft.value = it.take(12_000) },
                    label = { Text(stringResource(R.string.annotation_note_label)) },
                    minLines = 3,
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(
                    enabled = noteDraft.value.isNotBlank(),
                    onClick = {
                        val existing = editingAnnotation.value
                        if (existing == null) onAddNote(noteDraft.value.trim())
                        else onUpdateAnnotation(existing, noteDraft.value.trim())
                        showNoteEditor.value = false
                    }
                ) { Text(stringResource(R.string.annotation_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoteEditor.value = false }) { Text(stringResource(R.string.annotation_cancel)) }
            }
        )
    }

    pendingDelete.value?.let { annotation ->
        val label = stringResource(
            when (annotation.kind) {
                "NOTE" -> R.string.annotation_kind_note
                "HIGHLIGHT" -> R.string.annotation_kind_highlight
                else -> R.string.annotation_kind_bookmark
            }
        )
        AlertDialog(
            onDismissRequest = { pendingDelete.value = null },
            title = { Text(stringResource(R.string.annotation_delete_title, label)) },
            text = {
                Text(
                    annotation.noteText?.takeIf { it.isNotBlank() }
                        ?: annotation.selectedText?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.annotation_delete_message)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete.value = null
                    onDeleteAnnotation(annotation)
                }) { Text(stringResource(R.string.annotation_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete.value = null }) {
                    Text(stringResource(R.string.annotation_cancel))
                }
            }
        )
    }

}
