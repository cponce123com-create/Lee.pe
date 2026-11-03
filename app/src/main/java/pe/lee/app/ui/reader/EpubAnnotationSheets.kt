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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.app.data.local.ReadingAnnotationEntity
import androidx.compose.runtime.MutableState



@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EpubAnnotationSheets(
    annotations: List<ReadingAnnotationEntity>,
    showAnnotations: MutableState<Boolean>,
    showNoteEditor: MutableState<Boolean>,
    editingAnnotation: MutableState<ReadingAnnotationEntity?>,
    noteDraft: MutableState<String>,
    onAddBookmark: () -> Unit,
    onAddNote: (String) -> Unit,
    onUpdateAnnotation: (ReadingAnnotationEntity, String) -> Unit,
    onChangeHighlightColor: (ReadingAnnotationEntity, Int) -> Unit,
    onSelectAnnotation: (ReadingAnnotationEntity) -> Unit,
    onDeleteAnnotation: (ReadingAnnotationEntity) -> Unit
) {
    if (showAnnotations.value) {
        ModalBottomSheet(onDismissRequest = { showAnnotations.value = false }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Marcadores, notas y resaltados", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = {
                        editingAnnotation.value = null
                        noteDraft.value = ""
                        showNoteEditor.value = true
                    }) { Text("Añadir nota") }
                    TextButton(onClick = onAddBookmark) { Text("Guardar") }
                }
            }
            if (annotations.isEmpty()) {
                Text(
                    "Todavía no guardaste marcadores, notas ni resaltados en este libro.",
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
                                            "Resaltado · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(annotation.createdAt))}"
                                        } else {
                                            "Marcador · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(annotation.createdAt))}"
                                        },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (annotation.kind == "HIGHLIGHT") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        listOf(
                                            "Amarillo" to 0xFFFFD54F.toInt(),
                                            "Azul" to 0xFF64B5F6.toInt(),
                                            "Verde" to 0xFF81C784.toInt(),
                                            "Rosa" to 0xFFF48FB1.toInt()
                                        ).forEach { (label, color) ->
                                            IconButton(
                                                onClick = { onChangeHighlightColor(annotation, color) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Circle,
                                                    contentDescription = "Resaltado $label",
                                                    tint = Color(color),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    when (annotation.kind) {
                                        "NOTE" -> "Nota · abrir posición"
                                        "HIGHLIGHT" -> "Resaltado · abrir texto"
                                        else -> "Marcador · abrir posición"
                                    },
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
                                    Icon(Icons.Default.Edit, contentDescription = "Editar nota")
                                }
                            }
                            IconButton(onClick = { onDeleteAnnotation(annotation) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar marcador")
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
            title = { Text(if (editingAnnotation.value == null) "Nueva nota" else "Editar nota") },
            text = {
                OutlinedTextField(
                    value = noteDraft.value,
                    onValueChange = { noteDraft.value = it },
                    label = { Text("Nota para esta posición") },
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
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteEditor.value = false }) { Text("Cancelar") }
            }
        )
    }

}
