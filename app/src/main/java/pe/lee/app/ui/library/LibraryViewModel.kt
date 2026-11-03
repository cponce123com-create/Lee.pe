package pe.lee.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.CollectionEntity
import pe.lee.app.data.repository.AnnotationRepository
import pe.lee.app.data.repository.BookRepository
import pe.lee.app.data.repository.ReadingStatsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LibraryState(
    val books: List<BookEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList()
)

class LibraryViewModel(
    private val booksRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val statsRepository: ReadingStatsRepository
) : ViewModel() {
    val state: StateFlow<LibraryState> = combine(
        booksRepository.observeBooks(),
        booksRepository.observeCollections()
    ) { books, collections -> LibraryState(books, collections) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun observeDurationForDay(day: String) = statsRepository.observeDurationForDay(day)
    fun observeReadingDayKeys() = statsRepository.observeReadingDayKeys()
    suspend fun findBook(uri: String) = booksRepository.findByUri(uri)
    suspend fun insertBook(book: BookEntity) = booksRepository.insertBooks(listOf(book))
    suspend fun insertBooks(books: List<BookEntity>) = booksRepository.insertBooks(books)
    suspend fun rebindUri(bookId: String, uri: String) = booksRepository.rebindUri(bookId, uri)
    suspend fun deleteBook(uri: String) = booksRepository.deleteBook(uri)
    suspend fun pendingCleanupUris() = booksRepository.pendingCleanupUris()
    suspend fun completeCleanup(uri: String) = booksRepository.completeCleanup(uri)
    suspend fun resetReadingStatistics() = statsRepository.reset()
    suspend fun updateCollection(uri: String, name: String?) = booksRepository.updateCollection(uri, name)
    suspend fun moveCollection(id: Long, direction: Int) = booksRepository.moveCollection(id, direction)
    suspend fun renameCollection(id: Long, name: String) = booksRepository.renameCollection(id, name)
    suspend fun deleteCollection(id: Long) = booksRepository.deleteCollection(id)
    suspend fun updateTitle(uri: String, title: String) = booksRepository.updateTitle(uri, title)
    suspend fun updateFavorite(uri: String, favorite: Boolean) = booksRepository.updateFavorite(uri, favorite)
    suspend fun annotationsForBook(uri: String) = annotationRepository.getForBook(uri)
}
