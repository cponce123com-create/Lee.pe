package pe.lee.app.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

/** A backup stores metadata, never EPUB/PDF/audio bytes or a portable file grant. */
data class LocalBackupSnapshot(
    val books: List<BookEntity>,
    val annotations: List<ReadingAnnotationEntity>,
    val sessions: List<ReadingSessionEntity>
)

data class LocalBackupMergeReport(
    val linkedBooks: Int,
    val skippedBooks: Int,
    val linkedByTitle: Int,
    val addedAnnotations: Int,
    val restoredSessionDays: Int,
    val uriMap: Map<String, String>
)

@Dao
interface LocalBackupDao {
    @Query("SELECT * FROM library_books ORDER BY uri")
    suspend fun allBooks(): List<BookEntity>

    @Query("SELECT * FROM reading_annotations ORDER BY bookUri, id")
    suspend fun allAnnotations(): List<ReadingAnnotationEntity>

    @Query("SELECT * FROM reading_sessions ORDER BY bookUri, dayKey")
    suspend fun allSessions(): List<ReadingSessionEntity>

    @Transaction
    suspend fun snapshot(): LocalBackupSnapshot =
        LocalBackupSnapshot(allBooks(), allAnnotations(), allSessions())

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnnotationIfMissing(item: ReadingAnnotationEntity): Long

    @Query("""
        UPDATE library_books
        SET epubLocator = :locator,
            pdfPageIndex = :pdfPage,
            readProgress = :progress,
            lastOpenedAt = :lastOpened
        WHERE uri = :uri
    """)
    suspend fun updateReadingPosition(
        uri: String, locator: String?, pdfPage: Int, progress: Float, lastOpened: Long?
    )

    @Query("""
        UPDATE library_books
        SET isFavorite = :favorite, collectionId = :collection
        WHERE uri = :uri
    """)
    suspend fun updateRestoredOrganization(uri: String, favorite: Boolean, collection: String?)

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
                    match.uri, book.epubLocator, book.pdfPageIndex,
                    book.readProgress.coerceIn(0f, 1f), book.lastOpenedAt
                )
            }
            updateRestoredOrganization(
                match.uri, match.isFavorite || book.isFavorite,
                match.collectionId ?: book.collectionId
            )
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
            uriMap = mapped
        )
    }
}

/**
 * Indexed matching keeps backup previews and large-library imports O(n)
 * instead of repeatedly scanning thousands of locally imported titles.
 */
internal class LocalBackupMatcher(current: List<BookEntity>) {
    private val byUri = current.associateBy { it.uri }
    private val byTitleAndFormat = current.groupBy {
        it.title.trim().lowercase(java.util.Locale.ROOT) to
            it.format.trim().lowercase(java.util.Locale.ROOT)
    }

    fun find(saved: BookEntity): BookEntity? =
        byUri[saved.uri] ?: byTitleAndFormat[
            saved.title.trim().lowercase(java.util.Locale.ROOT) to
                saved.format.trim().lowercase(java.util.Locale.ROOT)
        ]?.singleOrNull()
}

/**
 * Exact URI first. The fallback requires a unique local title and format
 * match; never guess when there are multiple editions with identical titles.
 */
internal fun findBackupMatch(
    saved: BookEntity,
    current: List<BookEntity>
): BookEntity? {
    return LocalBackupMatcher(current).find(saved)
}
