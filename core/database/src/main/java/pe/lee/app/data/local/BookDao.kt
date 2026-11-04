package pe.lee.app.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Dao
interface BookDao {
    @Query("SELECT * FROM library_books ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookRow(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookRows(books: List<BookEntity>)

    @Query("DELETE FROM pending_book_cleanup WHERE uri = :uri")
    suspend fun cancelPendingCleanup(uri: String)

    @Transaction
    suspend fun insert(book: BookEntity) {
        cancelPendingCleanup(book.uri)
        insertBookRow(book)
    }

    @Transaction
    suspend fun insertAll(books: List<BookEntity>) {
        books.forEach { cancelPendingCleanup(it.uri) }
        insertBookRows(books)
    }

    @Query("SELECT * FROM library_books WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): BookEntity?

    @Query("SELECT * FROM library_books WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BookEntity?

    @Query("UPDATE library_books SET lastOpenedAt = :openedAt WHERE uri = :uri")
    suspend fun markOpened(uri: String, openedAt: Long)

    @Query("""
        UPDATE library_books
        SET epubLocator = :locatorJson, readProgress = :progress, lastOpenedAt = :openedAt
        WHERE uri = :uri
    """)
    suspend fun saveEpubProgress(uri: String, locatorJson: String, progress: Float, openedAt: Long)

    @Query("""
        UPDATE library_books
        SET pdfPageIndex = :pageIndex, readProgress = :progress, lastOpenedAt = :openedAt
        WHERE uri = :uri
    """)
    suspend fun savePdfProgress(uri: String, pageIndex: Int, progress: Float, openedAt: Long)

    @Query("UPDATE library_books SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE library_books SET title = :title WHERE uri = :uri")
    suspend fun updateTitle(uri: String, title: String)

    @Query("UPDATE library_books SET collectionId = :collectionName WHERE uri = :uri")
    suspend fun updateLegacyCollection(uri: String, collectionName: String?)

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("""
        SELECT book_collections.bookId AS bookId, collections.name AS collectionName
        FROM book_collections
        JOIN collections ON collections.id = book_collections.collectionId
        ORDER BY collections.sortOrder, collections.name COLLATE NOCASE
    """)
    fun observeBookCollectionNames(): Flow<List<BookCollectionAssignment>>

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getCollections(): List<CollectionEntity>

    @Query("SELECT collections.name FROM collections JOIN book_collections ON collections.id = book_collections.collectionId JOIN library_books ON library_books.id = book_collections.bookId WHERE library_books.uri = :uri ORDER BY collections.sortOrder, collections.name COLLATE NOCASE")
    suspend fun collectionNamesForBook(uri: String): List<String>

    @Query("SELECT id FROM collections WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findCollectionId(normalizedName: String): Long?

    @androidx.room3.Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("DELETE FROM book_collections WHERE bookId = :bookId")
    suspend fun clearBookCollections(bookId: String)

    @androidx.room3.Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookCollection(link: BookCollectionCrossRef)

    @Query("UPDATE collections SET name = :name, normalizedName = :normalizedName WHERE id = :id")
    suspend fun renameCollectionRow(id: Long, name: String, normalizedName: String)

    @Query("UPDATE collections SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setCollectionOrder(id: Long, sortOrder: Int)

    @Transaction
    suspend fun moveCollection(id: Long, direction: Int) {
        val ordered = getCollections()
        val oldIndex = ordered.indexOfFirst { it.id == id }
        if (oldIndex < 0) return
        val newIndex = (oldIndex + direction.coerceIn(-1, 1)).coerceIn(0, ordered.lastIndex)
        if (newIndex == oldIndex) return
        val mutable = ordered.toMutableList()
        val item = mutable.removeAt(oldIndex)
        mutable.add(newIndex, item)
        mutable.forEachIndexed { index, collection -> setCollectionOrder(collection.id, index) }
    }

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollectionRow(id: Long)

    @Query("UPDATE library_books SET collectionId = NULL WHERE collectionId IS NOT NULL AND LOWER(TRIM(collectionId)) = :normalizedName")
    suspend fun clearLegacyCollectionName(normalizedName: String)

    @Transaction
    suspend fun updateCollection(uri: String, collectionName: String?) {
        val book = findByUri(uri) ?: return
        clearBookCollections(book.id)
        val normalized = collectionName?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
        if (normalized == null) {
            updateLegacyCollection(uri, null)
            return
        }
        val displayName = collectionName.trim()
        val collectionId = findCollectionId(normalized)
            ?: insertCollection(CollectionEntity(name = displayName, normalizedName = normalized))
        val resolvedId = if (collectionId == -1L) findCollectionId(normalized) else collectionId
        if (resolvedId != null) {
            addBookCollection(BookCollectionCrossRef(book.id, resolvedId))
            updateLegacyCollection(uri, collectionName(resolvedId) ?: displayName)
        } else {
            updateLegacyCollection(uri, displayName)
        }
    }

    @Transaction
    suspend fun renameCollection(id: Long, newName: String) {
        val displayName = newName.trim()
        require(displayName.isNotEmpty()) { "El nombre de la colección no puede estar vacío." }
        val normalized = displayName.lowercase(Locale.ROOT)
        val existing = findCollectionId(normalized)
        require(existing == null || existing == id) { "Ya existe una colección con ese nombre." }
        renameCollectionRow(id, displayName, normalized)
        // Keep the legacy export field in sync until the next backup format removes it.
        val books = booksInCollection(id)
        books.forEach { updateLegacyCollection(it.uri, displayName) }
    }

    @Query("SELECT library_books.* FROM library_books JOIN book_collections ON library_books.id = book_collections.bookId WHERE book_collections.collectionId = :collectionId")
    suspend fun booksInCollection(collectionId: Long): List<BookEntity>

    @Transaction
    suspend fun deleteCollection(id: Long) {
        val affectedBooks = booksInCollection(id)
        deleteCollectionRow(id)
        affectedBooks.forEach { book ->
            updateLegacyCollection(book.uri, collectionNamesForBook(book.uri).firstOrNull())
        }
    }

    @Query("SELECT name FROM collections WHERE id = :id LIMIT 1")
    suspend fun collectionName(id: Long): String?

    @Transaction
    suspend fun rebindUri(bookId: String, newUri: String): Boolean {
        val book = findById(bookId) ?: return false
        if (book.uri == newUri) return true
        if (findByUri(newUri) != null) return false
        updateAnnotationsUri(book.uri, newUri)
        updateSessionsUri(book.uri, newUri)
        updateBookUri(bookId, newUri)
        return true
    }

    @Query("UPDATE reading_annotations SET bookUri = :newUri WHERE bookUri = :oldUri")
    suspend fun updateAnnotationsUri(oldUri: String, newUri: String)

    @Query("UPDATE reading_sessions SET bookUri = :newUri WHERE bookUri = :oldUri")
    suspend fun updateSessionsUri(oldUri: String, newUri: String)

    @Query("UPDATE library_books SET uri = :newUri WHERE id = :bookId")
    suspend fun updateBookUri(bookId: String, newUri: String)

    @Query("DELETE FROM reading_annotations WHERE bookUri = :uri")
    suspend fun deleteAnnotationsByBookUri(uri: String)

    @Query("DELETE FROM reading_sessions WHERE bookUri = :uri")
    suspend fun deleteSessionsByBookUri(uri: String)

    @Query("DELETE FROM book_collections WHERE bookId = :bookId")
    suspend fun deleteCollectionLinks(bookId: String)

    @Query("DELETE FROM library_books WHERE uri = :uri")
    suspend fun deleteLibraryBookByUri(uri: String)

    @androidx.room3.Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueBookCleanup(item: PendingBookCleanupEntity)

    @Query("SELECT uri FROM pending_book_cleanup ORDER BY uri")
    suspend fun pendingBookCleanupUris(): List<String>

    @Query("DELETE FROM pending_book_cleanup WHERE uri = :uri")
    suspend fun completeBookCleanup(uri: String)

    @Transaction
    suspend fun deleteByUri(uri: String) {
        val book = findByUri(uri) ?: return
        enqueueBookCleanup(PendingBookCleanupEntity(uri))
        deleteAnnotationsByBookUri(uri)
        deleteSessionsByBookUri(uri)
        deleteCollectionLinks(book.id)
        deleteLibraryBookByUri(uri)
    }
}
