package pe.lee.app.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import java.util.Locale

/** A backup stores metadata, never EPUB/PDF/audio bytes or a portable file grant. */
data class LocalBackupSnapshot(
    val books: List<BookEntity>,
    val annotations: List<ReadingAnnotationEntity>,
    val sessions: List<ReadingSessionEntity>,
    /** All collections, including empty ones, so a restore keeps the user's shelves. */
    val collections: List<CollectionEntity> = emptyList(),
    /** Book URI in the backup -> names of every collection it belongs to. */
    val bookCollectionNames: Map<String, List<String>> = emptyMap(),
)

/** Projection used to rebuild [LocalBackupSnapshot.bookCollectionNames] in one query. */
data class BookCollectionName(val bookUri: String, val collectionName: String)

data class LocalBackupMergeReport(
    val linkedBooks: Int,
    val skippedBooks: Int,
    val linkedByTitle: Int,
    val addedAnnotations: Int,
    val restoredSessionDays: Int,
    val uriMap: Map<String, String>,
)

@Dao
interface LocalBackupDao {
    @Query("SELECT * FROM library_books ORDER BY uri")
    suspend fun allBooks(): List<BookEntity>

    @Query("SELECT * FROM reading_annotations ORDER BY bookUri, id")
    suspend fun allAnnotations(): List<ReadingAnnotationEntity>

    @Query("SELECT * FROM reading_sessions ORDER BY bookUri, dayKey")
    suspend fun allSessions(): List<ReadingSessionEntity>

    @Query("SELECT * FROM collections ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun allCollections(): List<CollectionEntity>

    @Query(
        """
        SELECT library_books.uri AS bookUri, collections.name AS collectionName
        FROM book_collections
        JOIN library_books ON library_books.id = book_collections.bookId
        JOIN collections ON collections.id = book_collections.collectionId
        ORDER BY library_books.uri, collections.sortOrder, collections.name COLLATE NOCASE
    """,
    )
    suspend fun allBookCollectionNames(): List<BookCollectionName>

    @Transaction
    suspend fun snapshot(): LocalBackupSnapshot = LocalBackupSnapshot(
        books = allBooks(),
        annotations = allAnnotations(),
        sessions = allSessions(),
        collections = allCollections(),
        bookCollectionNames = allBookCollectionNames()
            .groupBy({ it.bookUri }, { it.collectionName }),
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnnotationIfMissing(item: ReadingAnnotationEntity): Long

    @Query(
        """
        UPDATE library_books
        SET epubLocator = :locator,
            pdfPageIndex = :pdfPage,
            readProgress = :progress,
            lastOpenedAt = :lastOpened
        WHERE uri = :uri
    """,
    )
    suspend fun updateReadingPosition(uri: String, locator: String?, pdfPage: Int, progress: Float, lastOpened: Long?)

    @Query("UPDATE library_books SET isFavorite = :favorite WHERE uri = :uri")
    suspend fun updateRestoredFavorite(uri: String, favorite: Boolean)

    @Query("SELECT id FROM library_books WHERE uri = :uri LIMIT 1")
    suspend fun bookIdForUri(uri: String): String?

    @Query("DELETE FROM book_collections WHERE bookId = :bookId")
    suspend fun clearRestoredCollections(bookId: String)

    @Query("UPDATE library_books SET collectionId = :name WHERE uri = :uri")
    suspend fun updateRestoredLegacyCollection(uri: String, name: String?)

    @Query("SELECT id FROM collections WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findCollectionId(normalizedName: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookCollection(link: BookCollectionCrossRef)

    @Query("SELECT name FROM collections WHERE id = :id LIMIT 1")
    suspend fun collectionName(id: Long): String?

    /**
     * Merges the organization of a restored book. Version 1 backups only carry
     * one legacy collection name; version 2 carries every collection, so a book
     * that belonged to "Novela" and "Perú" comes back on both shelves.
     */
    @Transaction
    suspend fun updateRestoredOrganization(uri: String, favorite: Boolean, collections: List<String>) {
        updateRestoredFavorite(uri, favorite)
        val bookId = bookIdForUri(uri) ?: return
        val hasExistingCollections = linkedCollectionNames(bookId).isNotEmpty()
        val uniqueNames = collections
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        for (displayName in uniqueNames) {
            val normalizedName = displayName.lowercase(Locale.ROOT)
            val insertedId = findCollectionId(normalizedName)
                ?: insertCollection(
                    CollectionEntity(name = displayName, normalizedName = normalizedName),
                )
            val resolvedId = if (insertedId == -1L) findCollectionId(normalizedName) else insertedId
            if (resolvedId != null) {
                insertBookCollection(BookCollectionCrossRef(bookId, resolvedId))
            }
        }
        // Do not replace the user's pre-existing legacy shelf while merging a backup.
        // This column remains a compatibility field for older library queries.
        if (!hasExistingCollections) {
            updateRestoredLegacyCollection(uri, uniqueNames.firstOrNull())
        }
    }

    @Query(
        "SELECT collections.name FROM collections JOIN book_collections ON collections.id = book_collections.collectionId WHERE book_collections.bookId = :bookId",
    )
    suspend fun linkedCollectionNames(bookId: String): List<String>

    @Query("SELECT * FROM reading_sessions WHERE dayKey = :day AND bookUri = :uri LIMIT 1")
    suspend fun findSession(day: String, uri: String): ReadingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(item: ReadingSessionEntity)

    /**
     * Only attach records to files the user has ALREADY imported. In particular we
     * never insert an inaccessible content:// URI from a different phone.
     * Same title/format is a convenience, exposed explicitly in the preview.
     */
    @Transaction
    suspend fun merge(snapshot: LocalBackupSnapshot): LocalBackupMergeReport {
        val current = allBooks()
        val usedUris = HashSet<String>()
        val mapped = LinkedHashMap<String, String>()
        val matcher = LocalBackupMatcher(current)
        var byTitle = 0
        for (book in snapshot.books) {
            val match = matcher.find(book) ?: continue
            if (!usedUris.add(match.uri)) continue
            mapped[book.uri] = match.uri
            if (book.uri != match.uri) byTitle++
            if ((book.lastOpenedAt ?: -1L) > (match.lastOpenedAt ?: -1L) ||
                (match.readProgress == 0f && book.readProgress > 0f && match.lastOpenedAt == null)
            ) {
                updateReadingPosition(
                    match.uri,
                    book.epubLocator,
                    book.pdfPageIndex,
                    book.readProgress.coerceIn(0f, 1f),
                    book.lastOpenedAt,
                )
            }
            // Version 2 archives carry every shelf of the book; version 1 only
            // had the legacy single name, so fall back to it when absent.
            val restoredCollections = snapshot.bookCollectionNames[book.uri].orEmpty()
                .ifEmpty { listOfNotNull(match.collectionId ?: book.collectionId) }
            updateRestoredOrganization(
                match.uri,
                match.isFavorite || book.isFavorite,
                restoredCollections,
            )
        }
        // Recreate shelves that had no books, so an empty collection survives a
        // backup/restore cycle instead of disappearing.
        for (collection in snapshot.collections) {
            val displayName = collection.name.trim()
            val normalizedName = displayName.lowercase(Locale.ROOT)
            if (normalizedName.isEmpty()) continue
            if (findCollectionId(normalizedName) == null) {
                insertCollection(
                    CollectionEntity(name = displayName, normalizedName = normalizedName),
                )
            }
        }
        var annotationCount = 0
        for (item in snapshot.annotations) {
            val localUri = mapped[item.bookUri] ?: continue
            if (insertAnnotationIfMissing(item.copy(bookUri = localUri)) != -1L) annotationCount++
        }
        var sessionCount = 0
        val existingSessions = allSessions().associateBy { it.dayKey to it.bookUri }.toMutableMap()
        for (session in snapshot.sessions) {
            val localUri = mapped[session.bookUri] ?: continue
            val key = session.dayKey to localUri
            val old = existingSessions[key]
            if (old == null || old.durationSeconds < session.durationSeconds) {
                val adjusted = session.copy(bookUri = localUri)
                upsertSession(adjusted)
                existingSessions[key] = adjusted
                sessionCount++
            }
        }
        return LocalBackupMergeReport(
            linkedBooks = mapped.size,
            skippedBooks = snapshot.books.size - mapped.size,
            linkedByTitle = byTitle,
            addedAnnotations = annotationCount,
            restoredSessionDays = sessionCount,
            uriMap = mapped,
        )
    }
}

/**
 * Indexed matching keeps backup previews and large-library imports O(n)
 * instead of repeatedly scanning thousands of locally imported titles.
 */
class LocalBackupMatcher(current: List<BookEntity>) {
    private val byUri = current.associateBy { it.uri }
    private val byTitleAndFormat = current.groupBy {
        it.title.trim().lowercase(java.util.Locale.ROOT) to
            it.format.trim().lowercase(java.util.Locale.ROOT)
    }

    fun find(saved: BookEntity): BookEntity? = byUri[saved.uri] ?: byTitleAndFormat[
        saved.title.trim().lowercase(java.util.Locale.ROOT) to
            saved.format.trim().lowercase(java.util.Locale.ROOT),
    ]?.singleOrNull()
}

/**
 * Exact URI first. The fallback requires a unique local title and format
 * match; never guess when there are multiple editions with identical titles.
 */
fun findBackupMatch(saved: BookEntity, current: List<BookEntity>): BookEntity? = LocalBackupMatcher(current).find(saved)
