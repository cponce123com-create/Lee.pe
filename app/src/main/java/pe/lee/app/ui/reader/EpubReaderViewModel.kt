package pe.lee.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.ReadingAnnotationEntity
import pe.lee.app.data.repository.AnnotationRepository
import pe.lee.app.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class EpubReaderViewModel(
    private val books: BookRepository,
    private val annotations: AnnotationRepository
) : ViewModel() {
    private val currentUri = MutableStateFlow<String?>(null)

    val annotationState: StateFlow<List<ReadingAnnotationEntity>> = currentUri
        .flatMapLatest { uri -> uri?.let(annotations::observeForBook) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openBook(uri: String) { currentUri.value = uri }
    suspend fun findBook(uri: String): BookEntity? = books.findByUri(uri)
    suspend fun markOpened(uri: String, at: Long) = books.markOpened(uri, at)
    suspend fun saveProgress(uri: String, locatorJson: String, progress: Float, openedAt: Long) =
        books.saveEpubProgress(uri, locatorJson, progress, openedAt)
    suspend fun saveAnnotation(annotation: ReadingAnnotationEntity) = annotations.save(annotation)
    suspend fun updateAnnotationColor(id: String, color: Int, at: Long) = annotations.updateColor(id, color, at)
    suspend fun updateAnnotationNote(id: String, note: String?, at: Long) = annotations.updateNote(id, note, at)
    suspend fun deleteAnnotation(id: String) = annotations.delete(id)
}
