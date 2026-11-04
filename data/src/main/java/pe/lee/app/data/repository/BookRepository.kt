package pe.lee.app.data.repository

import kotlinx.coroutines.flow.Flow
import pe.lee.app.data.local.BookDao
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.CollectionEntity
import pe.lee.core.domain.ReaderBook
import pe.lee.core.domain.ReaderBookRepository
import pe.lee.core.domain.ReadingProgress
import pe.lee.core.domain.library.UpdateBookProgressUseCase

class BookRepository(private val dao: BookDao) : ReaderBookRepository {
    private val updateProgress = UpdateBookProgressUseCase()
    fun observeBooks(): Flow<List<BookEntity>> = dao.observeAll()
    fun observeCollections(): Flow<List<CollectionEntity>> = dao.observeCollections()
    fun observeBookCollectionNames() = dao.observeBookCollectionNames()
    suspend fun findByUri(uri: String) = dao.findByUri(uri)
    suspend fun findById(id: String) = dao.findById(id)
    override suspend fun findReaderBook(uri: String): ReaderBook? = dao.findByUri(uri)?.let { book ->
        ReaderBook(
            id = book.id,
            uri = book.uri,
            title = book.title,
            format = book.format,
            readProgress = ReadingProgress.from(book.readProgress).fraction,
            epubLocator = book.epubLocator,
            pdfPageIndex = book.pdfPageIndex,
        )
    }
    suspend fun insertBooks(books: List<BookEntity>) = dao.insertAll(
        books.map { book ->
            book.copy(readProgress = ReadingProgress.from(book.readProgress).fraction)
        },
    )
    override suspend fun markOpened(uri: String, at: Long) = dao.markOpened(uri, at)
    override suspend fun saveEpubProgress(uri: String, locator: String, progress: Float, at: Long) =
        updateProgress(progress) { dao.saveEpubProgress(uri, locator, it, at) }
    override suspend fun savePdfProgress(uri: String, page: Int, progress: Float, at: Long) =
        updateProgress(progress) { dao.savePdfProgress(uri, page, it, at) }
    suspend fun updateFavorite(uri: String, value: Boolean) = dao.updateFavorite(uri, value)
    suspend fun updateTitle(uri: String, title: String) = dao.updateTitle(uri, title)
    suspend fun updateCollection(uri: String, name: String?) = dao.updateCollection(uri, name)
    suspend fun collectionNamesForBook(uri: String) = dao.collectionNamesForBook(uri)
    suspend fun getCollections() = dao.getCollections()
    suspend fun moveCollection(id: Long, direction: Int) = dao.moveCollection(id, direction)
    suspend fun renameCollection(id: Long, name: String) = dao.renameCollection(id, name)
    suspend fun deleteCollection(id: Long) = dao.deleteCollection(id)
    suspend fun rebindUri(bookId: String, uri: String) = dao.rebindUri(bookId, uri)
    suspend fun deleteBook(uri: String) = dao.deleteByUri(uri)
    suspend fun pendingCleanupUris() = dao.pendingBookCleanupUris()
    suspend fun completeCleanup(uri: String) = dao.completeBookCleanup(uri)
}
