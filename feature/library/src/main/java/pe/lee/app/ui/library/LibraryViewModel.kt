package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.CollectionEntity
import pe.lee.app.data.repository.BookRepository
import pe.lee.core.domain.ReadingStatisticsRepository
import pe.lee.core.domain.ReaderAnnotationRepository
import pe.lee.feature.library.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryState(
    val books: List<BookEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val bookCollectionNames: Map<String, List<String>> = emptyMap()
)

/** State of the SAF folder scan, owned by the ViewModel so a rotation cannot cancel it (A-36). */
internal data class ScannerUiState(
    val busy: Boolean = false,
    val folderName: String = "",
    val folders: Int = 0,
    val files: Int = 0,
    val books: Int = 0,
    val preview: LocalFolderScanner.Result? = null,
    val error: String? = null
)

class LibraryViewModel(
    private val booksRepository: BookRepository,
    private val annotationRepository: ReaderAnnotationRepository,
    private val statsRepository: ReadingStatisticsRepository
) : ViewModel() {
    val state: StateFlow<LibraryState> = combine(
        booksRepository.observeBooks(),
        booksRepository.observeCollections(),
        booksRepository.observeBookCollectionNames()
    ) { books, collections, assignments ->
        LibraryState(
            books = books,
            collections = collections,
            bookCollectionNames = assignments.groupBy({ it.bookId }, { it.collectionName })
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun observeDurationForDay(day: String) = statsRepository.observeDurationForDay(day)
    fun observeReadingDayKeys() = statsRepository.observeReadingDayKeys()
    suspend fun findBook(uri: String) = booksRepository.findByUri(uri)
    suspend fun findBookById(id: String) = booksRepository.findById(id)
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
    suspend fun annotationsForBook(uri: String) = annotationRepository.getReaderAnnotations(uri)

    // --- Folder scanner -----------------------------------------------------
    // The scan used to run in the composition scope, so rotating the device
    // cancelled minutes of work without any message (A-36). It now lives here.

    private val mutableScanner = MutableStateFlow(ScannerUiState())
    internal val scanner: StateFlow<ScannerUiState> = mutableScanner.asStateFlow()
    private var scanJob: Job? = null

    internal fun scanFolder(context: Context, treeUri: Uri, alreadyImported: List<String>) {
        scanJob?.cancel()
        val appContext = context.applicationContext
        mutableScanner.update {
            it.copy(
                busy = true,
                preview = null,
                error = null,
                folders = 0,
                files = 0,
                books = 0,
                folderName = treeUri.lastPathSegment
                    ?.substringAfterLast(':')
                    ?.takeIf(String::isNotBlank)
                    ?: appContext.getString(R.string.library_selected_folder)
            )
        }
        scanJob = viewModelScope.launch {
            try {
                val result = LocalFolderScanner.scan(appContext, treeUri, alreadyImported) { progress ->
                    mutableScanner.update {
                        it.copy(
                            folders = progress.folders,
                            files = progress.files,
                            books = progress.books
                        )
                    }
                }
                mutableScanner.update { it.copy(preview = result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableScanner.update {
                    it.copy(
                        error = appContext.getString(R.string.scanner_import_failed),
                        busy = false
                    )
                }
            } finally {
                mutableScanner.update { it.copy(busy = false) }
            }
        }
    }

    internal fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        mutableScanner.update { it.copy(busy = false) }
    }

    internal fun clearScanPreview() = mutableScanner.update { it.copy(preview = null) }

    internal fun dismissScanError() = mutableScanner.update { it.copy(error = null) }
}
