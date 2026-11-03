package pe.lee.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.lee.core.domain.ReaderAnnotation
import pe.lee.core.domain.ReaderAnnotationRepository
import pe.lee.core.domain.ReaderBook
import pe.lee.core.domain.ReaderBookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class PdfReaderViewModel(
    private val books: ReaderBookRepository,
    private val annotations: ReaderAnnotationRepository
) : ViewModel() {
    private val currentUri = MutableStateFlow<String?>(null)

    val annotationState: StateFlow<List<ReaderAnnotation>> = currentUri
        .flatMapLatest { uri -> uri?.let(annotations::observeReaderAnnotations) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openBook(uri: String) { currentUri.value = uri }
    suspend fun findBook(uri: String): ReaderBook? = books.findReaderBook(uri)
    suspend fun markOpened(uri: String, at: Long) = books.markOpened(uri, at)
    suspend fun saveProgress(uri: String, pageIndex: Int, progress: Float, openedAt: Long) =
        books.savePdfProgress(uri, pageIndex, progress, openedAt)
    suspend fun saveAnnotation(annotation: ReaderAnnotation) = annotations.saveReaderAnnotation(annotation)
    suspend fun updateAnnotationColor(id: String, color: Int, at: Long) = annotations.updateReaderAnnotationColor(id, color, at)
    suspend fun updateAnnotationNote(id: String, note: String?, at: Long) = annotations.updateReaderAnnotationNote(id, note, at)
    suspend fun deleteAnnotation(id: String) = annotations.deleteReaderAnnotation(id)
}
