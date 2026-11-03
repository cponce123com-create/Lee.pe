package pe.lee.app.data.repository

import pe.lee.app.data.local.BookDao
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.CollectionEntity
import kotlinx.coroutines.flow.Flow

class BookRepository(private val dao: BookDao) {
    fun observeBooks(): Flow<List<BookEntity>> = dao.observeAll()
    fun observeCollections(): Flow<List<CollectionEntity>> = dao.observeCollections()
    suspend fun findByUri(uri: String) = dao.findByUri(uri)
    suspend fun findById(id: String) = dao.findById(id)
    suspend fun insertBooks(books: List<BookEntity>) = dao.insertAll(books)
    suspend fun markOpened(uri: String, at: Long) = dao.markOpened(uri, at)
    suspend fun saveEpubProgress(uri: String, locator: String, progress: Float, at: Long) =
        dao.saveEpubProgress(uri, locator, progress, at)
    suspend fun savePdfProgress(uri: String, page: Int, progress: Float, at: Long) =
        dao.savePdfProgress(uri, page, progress, at)
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
