package pe.lee.app.data.local

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pe.lee.app.data.repository.AudioProgressStore
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.ui.library.DeletedBookCleanup

@RunWith(AndroidJUnit4::class)
class LeeDatabaseMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migration4To5PreservesStableIdsAndCollectionLinks() = runBlocking {
        val name = "migration-4.db"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("pe.lee.app.data.local.LeeDatabase/4.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length()) {
                    legacy.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            legacy.execSQL(
                "INSERT INTO library_books (id, uri, title, format, importedAt, readProgress, pdfPageIndex, isFavorite) " +
                    "VALUES ('stable-id', ?, 'Versión cuatro', 'EPUB', 1, 0.5, 0, 1)",
                arrayOf(TEST_URI),
            )
            legacy.execSQL("INSERT INTO collections (id, name, normalizedName, sortOrder) VALUES (7, 'Lectura', 'lectura', 0)")
            legacy.execSQL("INSERT INTO book_collections (bookId, collectionId) VALUES ('stable-id', 7)")
            legacy.version = 4
        }
        val database = Room.databaseBuilder<LeeDatabase>(context, name)
            .addMigrations(*LeeDatabase.ALL_MIGRATIONS).build()
        try {
            val book = database.bookDao().findByUri(TEST_URI)
            assertEquals("stable-id", book?.id)
            assertEquals("Versión cuatro", book?.title)
            assertEquals(0.5f, book?.readProgress)
            assertEquals(true, book?.isFavorite)
            assertEquals(listOf("Lectura"), database.bookDao().collectionNamesForBook(TEST_URI))
            assertEquals(emptyList<String>(), database.bookDao().pendingBookCleanupUris())
            database.bookDao().deleteByUri(TEST_URI)
            assertEquals(listOf(TEST_URI), database.bookDao().pendingBookCleanupUris())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

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
                assertSessionBookUriIndex(databaseName)
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
                assertEquals(
                    "case-insensitive collection migration",
                    listOf("fantasía"),
                    database.bookDao().collectionNamesForBook(TEST_URI).map { it.lowercase(Locale.ROOT) },
                )
                assertEquals(
                    "duplicate legacy collection merged",
                    listOf("fantasía"),
                    database.bookDao().collectionNamesForBook(SECOND_URI).map { it.lowercase(Locale.ROOT) },
                )
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
        val offlineDirectory = File(context.noBackupFilesDir, "lee_pe_offline_books")
        offlineDirectory.deleteRecursively()
        val database = Room.databaseBuilder<LeeDatabase>(context, databaseName)
            .addMigrations(*LeeDatabase.ALL_MIGRATIONS)
            .build()
        try {
            val book = BookEntity(uri = TEST_URI, title = "Libro para borrar", format = "EPUB", importedAt = 1L)
            database.bookDao().insert(book)
            database.readingAnnotationDao().upsert(
                ReadingAnnotationEntity(
                    id = "annotation-1",
                    bookUri = TEST_URI,
                    kind = "NOTE",
                    locatorJson = "{}",
                    noteText = "Nota",
                    createdAt = 10L,
                ),
            )
            database.readingSessionDao().upsert(ReadingSessionEntity("2026-11-03", TEST_URI, 120L))
            database.bookDao().updateCollection(TEST_URI, "Lectura")
            val libraryPreferences = context.getSharedPreferences("lee_pe_library", 0)
            val preferenceKey = "test-setting:$TEST_URI"
            libraryPreferences.edit().putBoolean(preferenceKey, true).commit()
            AudioProgressStore.put(context, TEST_URI, 12_000L)

            assertEquals(listOf("Lectura"), database.bookDao().collectionNamesForBook(TEST_URI))
            database.bookDao().deleteByUri(TEST_URI)

            val snapshot = database.localBackupDao().snapshot()
            assertFalse(snapshot.books.any { it.uri == TEST_URI })
            assertFalse(snapshot.annotations.any { it.bookUri == TEST_URI })
            assertFalse(snapshot.sessions.any { it.bookUri == TEST_URI })
            assertEquals(emptyList<String>(), database.bookDao().collectionNamesForBook(TEST_URI))
            assertEquals(listOf(TEST_URI), database.bookDao().pendingBookCleanupUris())

            offlineDirectory.writeText("simulated storage cleanup failure")
            assertEquals(
                false,
                DeletedBookCleanup.complete(context, TEST_URI, SettingsRepository(context)) { uri ->
                    database.bookDao().completeBookCleanup(uri)
                },
            )
            assertEquals(listOf(TEST_URI), database.bookDao().pendingBookCleanupUris())
            assertEquals(true, libraryPreferences.contains(preferenceKey))
            assertEquals(12_000L, AudioProgressStore.get(context, TEST_URI))

            offlineDirectory.delete()
            assertEquals(true, offlineDirectory.mkdirs())
            val offlineCopy = File(offlineDirectory, "${sha256(TEST_URI)}.epub")
            offlineCopy.writeText("test copy")
            assertEquals(
                true,
                DeletedBookCleanup.complete(context, TEST_URI, SettingsRepository(context)) { uri ->
                    database.bookDao().completeBookCleanup(uri)
                },
            )
            assertFalse(offlineCopy.exists())
            assertFalse(libraryPreferences.contains(preferenceKey))
            assertEquals(0L, AudioProgressStore.get(context, TEST_URI))
            assertEquals(emptyList<String>(), database.bookDao().pendingBookCleanupUris())
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            offlineDirectory.deleteRecursively()
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
                ReadingAnnotationEntity("annotation-rebind", TEST_URI, "NOTE", "{}", noteText = "Se conserva", createdAt = 1L),
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

    private fun assertSessionBookUriIndex(databaseName: String) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { migrated ->
            migrated.rawQuery("PRAGMA index_list('reading_sessions')", null).use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue("Expected bookUri lookup index after migration", "index_reading_sessions_bookUri" in names)
            }
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
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "INSERT INTO library_books VALUES (?, ?, 'EPUB', 1, NULL, NULL, 0.65, 0, 0, ?)",
                arrayOf(TEST_URI, "Libro de prueba", "Fantasía"),
            )
            legacy.execSQL(
                "INSERT INTO library_books VALUES (?, ?, 'EPUB', 2, NULL, NULL, 0.1, 0, 0, ?)",
                arrayOf(SECOND_URI, "Segundo libro", "FANTASÍA"),
            )
            if (version >= 2) {
                legacy.execSQL(
                    """CREATE TABLE reading_annotations (
                        id TEXT NOT NULL, bookUri TEXT NOT NULL, kind TEXT NOT NULL,
                        locatorJson TEXT NOT NULL, selectedText TEXT, noteText TEXT,
                        colorArgb INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                legacy.execSQL("CREATE INDEX index_reading_annotations_bookUri_createdAt ON reading_annotations (bookUri, createdAt)")
                legacy.execSQL(
                    "INSERT INTO reading_annotations VALUES ('annotation-legacy', ?, 'NOTE', '{}', NULL, 'Nota antigua', NULL, 5, 5)",
                    arrayOf(TEST_URI),
                )
            }
            if (version >= 3) {
                legacy.execSQL(
                    """CREATE TABLE reading_sessions (
                        dayKey TEXT NOT NULL, bookUri TEXT NOT NULL, durationSeconds INTEGER NOT NULL,
                        PRIMARY KEY(dayKey, bookUri)
                    )
                    """.trimIndent(),
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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
