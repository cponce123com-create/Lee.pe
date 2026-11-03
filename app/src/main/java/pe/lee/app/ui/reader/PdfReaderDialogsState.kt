package pe.lee.app.ui.reader

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import pe.lee.core.domain.ReaderAnnotation

internal class PdfReaderDialogsState {
    val showPageDialog = mutableStateOf(false)
    val showPdfSearch = mutableStateOf(false)
    val pdfSearchQuery = mutableStateOf("")
    val pdfSearchResults = mutableStateOf<List<PdfSearchResult>>(emptyList())
    val pdfSearchMessage = mutableStateOf<String?>(null)
    val searchingPdf = mutableStateOf(false)
    val pdfSearchJob = mutableStateOf<Job?>(null)
    val showAnnotations = mutableStateOf(false)
    val showNoteEditor = mutableStateOf(false)
    val selectedPdfText = mutableStateOf<String?>(null)
    val selectedPdfPage = mutableIntStateOf(0)
    val selectedPdfRects = mutableStateOf<List<PdfNormalizedRect>>(emptyList())
    val showSelectionDialog = mutableStateOf(false)
    val quoteForNote = mutableStateOf<String?>(null)
    val editingNote = mutableStateOf<ReaderAnnotation?>(null)
    val noteDraft = mutableStateOf("")
    val requestedPage = mutableStateOf("1")
}
