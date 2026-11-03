package pe.lee.app.data.local

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LeeDatabaseMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrationsFromEverySupportedVersionPreserveLibraryNotesAndStatistics() = runBlocking {
        for (startingVersion in 1..3) {
            val databaseName = "migration-$startingVersion.db"
            context.deleteDatabase(databaseName)
            createLegacyDatabase(databaseName, startingVersion)

            val database = Room.databaseBuilder<LeeDatabase>(context, databaseName)
                .addMigrations(*LeeDatabase.ALL_MIGRATIONS)
                .build()
            try {
                val snapshot = database.localBackupDao().snapshot()
                assertEquals("version $startingVersion books", 2, snapshot.books.size)
                assertEquals("version $startingVersion title", "Libro de prueba", snapshot.books.first().title)
                assertEquals("version $startingVersion progress", 0.65f, snapshot.books.first().readProgress)
                assertEquals("version $startingVersion annotations", if (startingVersion >= 2) 1 else 0, snapshot.annotations.size)
                if (startingVersion >= 2) {
                    assertEquals("Nota antigua", snapshot.annotations.single().noteText)
                }
                assertEquals("version $startingVersion sessions", if (startingVersion >= 3) 1 else 0, snapshot.sessions.size)
                if (startingVersion >= 3) {
                    assertEquals(180L, snapshot.sessions.single().durationSeconds)
                }
                assertEquals("case-insensitive collection migration", listOf("fantasía"),
                    database.bookDao().collectionNamesForBook(TEST_URI).map { it.lowercase(Locale.ROOT) })
                assertEquals("duplicate legacy collection merged", listOf("fantasía"),
                    database.bookDao().collectionNamesForBook(SECOND_URI).map { it.lowercase(Locale.ROOT) })
            } finally {
                database.close()
                context.deleteDatabase(databaseName)
            }
        }
    }

    @Test
    fun deletingBookRemovesAnnotationsSessionsAndCollectionLinksInOneRoomTransaction() = runBlocking {
        val databaseName = "delete-transaction.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder<LeeDatabase>(context, databaseName)
            .addMigrations(*LeeDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val book = BookEntity(uri = TEST_URI, title = "Libro para borrar", format = "EPUB", importedAt = 1L)
            database.bookDao().insert(book)
            database.readingAnnotationDao().upsert(
                ReadingAnnotationEntity(
                    id = "annotation-1", bookUri = TEST_URI, kind = "NOTE", locatorJson = "{}",
                    noteText = "Nota", createdAt = 10L
                )
            )
            database.readingSessionDao().upsert(ReadingSessionEntity("2026-11-03", TEST_URI, 120L))
            database.bookDao().updateCollection(TEST_URI, "Lectura")

            assertEquals(listOf("Lectura"), database.bookDao().collectionNamesForBook(TEST_URI))
            database.bookDao().deleteByUri(TEST_URI)

            val snapshot = database.localBackupDao().snapshot()
            assertFalse(snapshot.books.any { it.uri == TEST_URI })
            assertFalse(snapshot.annotations.any { it.bookUri == TEST_URI })
            assertFalse(snapshot.sessions.any { it.bookUri == TEST_URI })
            assertEquals(emptyList<String>(), database.bookDao().collectionNamesForBook(TEST_URI))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun rebindingUriPreservesBookIdentityAnnotationsAndReadingStatistics() = runBlocking {
        val databaseName = "rebind-uri.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder<LeeDatabase>(context, databaseName)
            .addMigrations(*LeeDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val original = BookEntity(uri = TEST_URI, title = "Reenlazado", format = "EPUB", importedAt = 1L)
            database.bookDao().insert(original)
            database.readingAnnotationDao().upsert(
                ReadingAnnotationEntity("annotation-rebind", TEST_URI, "NOTE", "{}", noteText = "Se conserva", createdAt = 1L)
            )
            database.readingSessionDao().upsert(ReadingSessionEntity("2026-11-03", TEST_URI, 90L))

            assertEquals(true, database.bookDao().rebindUri(original.id, REBOUND_URI))
            val rebound = database.bookDao().findByUri(REBOUND_URI)
            assertNotNull(rebound)
            assertEquals(original.id, rebound?.id)
            assertEquals(REBOUND_URI, database.localBackupDao().allAnnotations().single().bookUri)
            assertEquals(REBOUND_URI, database.localBackupDao().allSessions().single().bookUri)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun collectionsCanBeRenamedReorderedDeletedAndCannotBeDuplicatedByCase() = runBlocking {
        val databaseName = "collection-management.db"
        context.deleteDatabase(databaseName)
        val database = Room.databaseBuilder<LeeDatabase>(context, databaseName)
            .addMigrations(*LeeDatabase.ALL_MIGRATIONS)
            .build()
        try {
            database.bookDao().insert(BookEntity(uri = TEST_URI, title = "Uno", format = "EPUB", importedAt = 1L))
            database.bookDao().insert(BookEntity(uri = SECOND_URI, title = "Dos", format = "EPUB", importedAt = 2L))
            database.bookDao().updateCollection(TEST_URI, "Fantasía")
            database.bookDao().updateCollection(SECOND_URI, "fantasía")
            assertEquals(1, database.bookDao().getCollections().size)

            database.bookDao().updateCollection(SECOND_URI, "Ensayo")
            var collections = database.bookDao().getCollections()
            val fantasy = collections.single { it.name == "Fantasía" }
            val essay = collections.single { it.name == "Ensayo" }
            database.bookDao().moveCollection(fantasy.id, -1)
            database.bookDao().renameCollection(essay.id, "No ficción")

            collections = database.bookDao().getCollections()
            assertEquals(listOf("Fantasía", "No ficción"), collections.map { it.name })
            database.bookDao().deleteCollection(fantasy.id)
            assertEquals(listOf("No ficción"), database.bookDao().getCollections().map { it.name })
            assertEquals(emptyList<String>(), database.bookDao().collectionNamesForBook(TEST_URI))
            assertEquals(listOf("No ficción"), database.bookDao().collectionNamesForBook(SECOND_URI))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createLegacyDatabase(name: String, version: Int) {
        val databaseFile: File = context.getDatabasePath(name)
        databaseFile.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            legacy.execSQL(
                """CREATE TABLE library_books (
                    uri TEXT NOT NULL, title TEXT NOT NULL, format TEXT NOT NULL,
                    importedAt INTEGER NOT NULL, lastOpenedAt INTEGER, epubLocator TEXT,
                    readProgress REAL NOT NULL, pdfPageIndex INTEGER NOT NULL,
                    isFavorite INTEGER NOT NULL, collectionId TEXT, PRIMARY KEY(uri)
                )""".trimIndent()
            )
            legacy.execSQL(
                "INSERT INTO library_books VALUES (?, ?, 'EPUB', 1, NULL, NULL, 0.65, 0, 0, ?)",
                arrayOf(TEST_URI, "Libro de prueba", "Fantasía")
            )
            legacy.execSQL(
                "INSERT INTO library_books VALUES (?, ?, 'EPUB', 2, NULL, NULL, 0.1, 0, 0, ?)",
                arrayOf(SECOND_URI, "Segundo libro", "FANTASÍA")
            )
            if (version >= 2) {
                legacy.execSQL(
                    """CREATE TABLE reading_annotations (
                        id TEXT NOT NULL, bookUri TEXT NOT NULL, kind TEXT NOT NULL,
                        locatorJson TEXT NOT NULL, selectedText TEXT, noteText TEXT,
                        colorArgb INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )""".trimIndent()
                )
                legacy.execSQL("CREATE INDEX index_reading_annotations_bookUri_createdAt ON reading_annotations (bookUri, createdAt)")
                legacy.execSQL(
                    "INSERT INTO reading_annotations VALUES ('annotation-legacy', ?, 'NOTE', '{}', NULL, 'Nota antigua', NULL, 5, 5)",
                    arrayOf(TEST_URI)
                )
            }
            if (version >= 3) {
                legacy.execSQL(
                    """CREATE TABLE reading_sessions (
                        dayKey TEXT NOT NULL, bookUri TEXT NOT NULL, durationSeconds INTEGER NOT NULL,
                        PRIMARY KEY(dayKey, bookUri)
                    )""".trimIndent()
                )
                legacy.execSQL("INSERT INTO reading_sessions VALUES ('2026-11-03', ?, 180)", arrayOf(TEST_URI))
            }
            legacy.version = version
        } finally {
            legacy.close()
        }
    }

    private companion object {
        val TEST_URI = Uri.parse("content://lee.test/book/one").toString()
        val SECOND_URI = Uri.parse("content://lee.test/book/two").toString()
        val REBOUND_URI = Uri.parse("content://lee.test/reauthorized/book/one").toString()
    }
}
