package pe.lee.app.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import pe.lee.app.R
import pe.lee.app.ui.reader.DEFAULT_PDF_HIGHLIGHT_COLOR
import pe.lee.app.ui.reader.PdfReaderViewModel
import pe.lee.app.ui.reader.pdfPageLocator
import pe.lee.app.ui.reader.pdfTextLocator
import pe.lee.core.domain.ReaderAnnotation
import pe.lee.feature.sharecard.R as ShareCardR
import pe.lee.feature.sharecard.navigation.ShareCardArgs
import pe.lee.feature.sharecard.navigation.shareCardIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfReaderDialogs(
    state: PdfReaderDialogsState,
    context: Context,
    uri: Uri,
    bookTitle: String,
    pageIndex: Int,
    pageCount: Int,
    annotations: List<ReaderAnnotation>,
    viewModel: PdfReaderViewModel,
    coroutineScope: CoroutineScope,
    moveToPage: (Int) -> Unit,
    searchPdf: (String) -> Unit,
) {
    var showPageDialog by state.showPageDialog
    var showPdfSearch by state.showPdfSearch
    var pdfSearchQuery by state.pdfSearchQuery
    var pdfSearchResults by state.pdfSearchResults
    var pdfSearchMessage by state.pdfSearchMessage
    var searchingPdf by state.searchingPdf
    var pdfSearchJob by state.pdfSearchJob
    var showAnnotations by state.showAnnotations
    var showNoteEditor by state.showNoteEditor
    var selectedPdfText by state.selectedPdfText
    var selectedPdfPage by state.selectedPdfPage
    var selectedPdfRects by state.selectedPdfRects
    var showSelectionDialog by state.showSelectionDialog
    var quoteForNote by state.quoteForNote
    var editingNote by state.editingNote
    var noteDraft by state.noteDraft
    var requestedPage by state.requestedPage
    // Deleting a PDF note used to be a single irreversible tap (A-33).
    var pendingDeleteAnnotation by remember { mutableStateOf<ReaderAnnotation?>(null) }
    if (showPdfSearch) {
        ModalBottomSheet(onDismissRequest = {
            pdfSearchJob?.cancel()
            showPdfSearch = false
        }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            ) {
                Text(stringResource(R.string.pdf_search_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (Build.VERSION.SDK_INT < 35) {
                    Text(
                        stringResource(R.string.pdf_search_requires_android15),
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                        color = Color(0xFFD3DCEC),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = pdfSearchQuery,
                            onValueChange = { pdfSearchQuery = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.pdf_search_query_label)) },
                            singleLine = true,
                        )
                        IconButton(
                            onClick = { searchPdf(pdfSearchQuery) },
                            enabled = !searchingPdf && pdfSearchQuery.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.pdf_search_start))
                        }
                    }
                    if (searchingPdf) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.pdf_search_in_progress), modifier = Modifier.weight(1f))
                            TextButton(onClick = { pdfSearchJob?.cancel() }) { Text(stringResource(R.string.pdf_cancel)) }
                        }
                    }
                    pdfSearchMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            color = Color(0xFFD3DCEC),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(pdfSearchResults) { result ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    moveToPage(result.pageIndex)
                                    showPdfSearch = false
                                }.padding(vertical = 8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.pdf_page_number, result.pageIndex + 1),
                                    color = Color(0xFF75DFFF),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(result.text, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAnnotations) {
        ModalBottomSheet(onDismissRequest = { showAnnotations = false }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.pdf_annotations_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = {
                        editingNote = null
                        noteDraft = ""
                        quoteForNote = null
                        showNoteEditor = true
                    }) { Text(stringResource(R.string.pdf_add_note)) }
                    TextButton(onClick = {
                        val now = System.currentTimeMillis()
                        coroutineScope.launch {
                            viewModel.saveAnnotation(
                                ReaderAnnotation(
                                    id = UUID.randomUUID().toString(),
                                    bookUri = uri.toString(),
                                    kind = "BOOKMARK",
                                    locatorJson = pdfPageLocator(pageIndex),
                                    createdAt = now,
                                ),
                            )
                        }
                    }) { Text(stringResource(R.string.pdf_bookmark_page)) }
                }
            }
            if (annotations.isEmpty()) {
                Text(
                    stringResource(R.string.pdf_annotations_empty),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(annotations, key = { it.id }) { annotation ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).clickable {
                                    val targetPage = runCatching {
                                        JSONObject(annotation.locatorJson).optInt("pageIndex", -1)
                                    }.getOrDefault(-1)
                                    if (targetPage in 0 until pageCount) moveToPage(targetPage)
                                    showAnnotations = false
                                }.padding(vertical = 12.dp, horizontal = 8.dp),
                            ) {
                                Text(
                                    annotation.noteText?.takeIf { it.isNotBlank() }
                                        ?: annotation.selectedText?.takeIf { it.isNotBlank() }
                                        ?: when (annotation.kind) {
                                            "NOTE" -> stringResource(R.string.pdf_note)
                                            "HIGHLIGHT" -> stringResource(R.string.pdf_annotation_highlight_text)
                                            else -> stringResource(R.string.pdf_annotation_bookmarked_page)
                                        },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        R.string.pdf_annotation_page_open,
                                        runCatching {
                                            JSONObject(annotation.locatorJson).optInt("pageIndex", -1) + 1
                                        }.getOrDefault(0),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                            if (annotation.kind == "HIGHLIGHT") {
                                Row {
                                    listOf(
                                        stringResource(R.string.pdf_color_yellow) to 0xFFFFD54F.toInt(),
                                        stringResource(R.string.pdf_color_blue) to 0xFF64B5F6.toInt(),
                                        stringResource(R.string.pdf_color_green) to 0xFF81C784.toInt(),
                                        stringResource(R.string.pdf_color_pink) to 0xFFF48FB1.toInt(),
                                    ).forEach { (label, color) ->
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.updateAnnotationColor(
                                                        annotation.id,
                                                        color,
                                                        System.currentTimeMillis(),
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Circle,
                                                contentDescription = stringResource(R.string.pdf_highlight_color, label),
                                                tint = Color(color),
                                                modifier = Modifier.size(19.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            if (annotation.kind == "NOTE") {
                                IconButton(onClick = {
                                    editingNote = annotation
                                    noteDraft = annotation.noteText.orEmpty()
                                    showNoteEditor = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.pdf_edit_note))
                                }
                            }
                            IconButton(onClick = { pendingDeleteAnnotation = annotation }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.pdf_delete_annotation))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    pendingDeleteAnnotation?.let { annotation ->
        AlertDialog(
            onDismissRequest = { pendingDeleteAnnotation = null },
            title = { Text(stringResource(R.string.pdf_delete_annotation)) },
            text = {
                Text(
                    annotation.noteText?.takeIf { it.isNotBlank() }
                        ?: annotation.selectedText?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.pdf_delete_annotation_body),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteAnnotation = null
                    coroutineScope.launch { viewModel.deleteAnnotation(annotation.id) }
                }) { Text(stringResource(R.string.pdf_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAnnotation = null }) { Text(stringResource(R.string.pdf_cancel)) }
            },
        )
    }
    if (showNoteEditor) {
        AlertDialog(
            onDismissRequest = { showNoteEditor = false },
            title = {
                Text(
                    if (editingNote == null) {
                        stringResource(R.string.pdf_new_note_title)
                    } else {
                        stringResource(R.string.pdf_edit_note_title)
                    },
                )
            },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it.take(12_000) },
                    label = { Text(stringResource(R.string.pdf_note_page_label)) },
                    minLines = 3,
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(enabled = noteDraft.isNotBlank(), onClick = {
                    val currentEdit = editingNote
                    coroutineScope.launch {
                        if (currentEdit == null) {
                            val now = System.currentTimeMillis()
                            viewModel.saveAnnotation(
                                ReaderAnnotation(
                                    id = UUID.randomUUID().toString(),
                                    bookUri = uri.toString(),
                                    kind = "NOTE",
                                    locatorJson = pdfPageLocator(quoteForNote?.let { selectedPdfPage } ?: pageIndex),
                                    selectedText = quoteForNote?.take(12_000),
                                    noteText = noteDraft.trim(),
                                    createdAt = now,
                                ),
                            )
                        } else {
                            viewModel.updateAnnotationNote(currentEdit.id, noteDraft.trim(), System.currentTimeMillis())
                        }
                    }
                    showNoteEditor = false
                }) { Text(stringResource(R.string.pdf_save)) }
            },
            dismissButton = { TextButton(onClick = { showNoteEditor = false }) { Text(stringResource(R.string.pdf_cancel)) } },
        )
    }
    if (showSelectionDialog && selectedPdfText != null) {
        AlertDialog(
            onDismissRequest = { showSelectionDialog = false },
            title = { Text(stringResource(R.string.pdf_selected_text_title, selectedPdfPage + 1)) },
            text = { Text(selectedPdfText.orEmpty()) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        TextButton(onClick = {
                            selectedPdfText?.let { selected ->
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText(context.getString(R.string.pdf_clipboard_label), selected),
                                )
                            }
                            showSelectionDialog = false
                        }) { Text(stringResource(R.string.pdf_copy)) }
                        TextButton(
                            enabled = selectedPdfRects.isNotEmpty(),
                            onClick = {
                                val now = System.currentTimeMillis()
                                coroutineScope.launch {
                                    viewModel.saveAnnotation(
                                        ReaderAnnotation(
                                            id = UUID.randomUUID().toString(),
                                            bookUri = uri.toString(),
                                            kind = "HIGHLIGHT",
                                            locatorJson = pdfTextLocator(selectedPdfPage, selectedPdfRects),
                                            selectedText = selectedPdfText?.take(12_000),
                                            colorArgb = DEFAULT_PDF_HIGHLIGHT_COLOR,
                                            createdAt = now,
                                        ),
                                    )
                                }
                                showSelectionDialog = false
                            },
                        ) { Text(stringResource(R.string.pdf_highlight)) }
                        TextButton(onClick = {
                            quoteForNote = selectedPdfText
                            noteDraft = ""
                            editingNote = null
                            showSelectionDialog = false
                            showNoteEditor = true
                        }) { Text(stringResource(R.string.pdf_note)) }
                    }
                    TextButton(onClick = {
                        val selected = selectedPdfText.orEmpty().trim()
                        if (selected.isNotEmpty()) {
                            context.startActivity(
                                shareCardIntent(
                                    context,
                                    ShareCardArgs(
                                        text = selected,
                                        bookId = uri.toString(),
                                        bookTitle = bookTitle,
                                        author = "",
                                        locatorJson = pdfTextLocator(selectedPdfPage, selectedPdfRects),
                                    ),
                                ),
                            )
                        }
                        showSelectionDialog = false
                    }) { Text(context.getString(ShareCardR.string.share_card_create_action)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectionDialog = false }) { Text(stringResource(R.string.pdf_close)) }
            },
        )
    }
    if (showPageDialog) {
        AlertDialog(
            onDismissRequest = { showPageDialog = false },
            title = { Text(stringResource(R.string.pdf_go_to_page_title)) },
            text = {
                TextField(
                    value = requestedPage,
                    onValueChange = { requestedPage = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.pdf_page_number_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    requestedPage.toIntOrNull()?.let { page ->
                        if (page in 1..pageCount) moveToPage(page - 1)
                    }
                    showPageDialog = false
                }) { Text(stringResource(R.string.pdf_go)) }
            },
            dismissButton = {
                TextButton(onClick = { showPageDialog = false }) { Text(stringResource(R.string.pdf_cancel)) }
            },
        )
    }
}
