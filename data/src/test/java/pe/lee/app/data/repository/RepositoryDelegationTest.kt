package pe.lee.app.data.repository

import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lee.app.data.local.BookCollectionName
import pe.lee.app.data.local.BookDao
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.CollectionEntity
import pe.lee.app.data.local.LocalBackupDao
import pe.lee.app.data.local.LocalBackupMergeReport
import pe.lee.app.data.local.LocalBackupSnapshot
import pe.lee.app.data.local.ReadingSessionDao
import pe.lee.app.data.local.ReadingSessionEntity
import pe.lee.core.domain.ReaderBook

class RepositoryDelegationTest {
    @Test
    fun bookRepositoryMapsLookupsNormalizesWritesAndClampsReaderProgress() = runBlocking {
        val source = BookEntity(
            id = "b1",
            uri = "content://a",
            title = "Title",
            format = "EPUB",
            importedAt = 9,
            readProgress = 1.4f,
            epubLocator = "loc",
            pdfPageIndex = 4,
        )
        val dao = BookDaoFake().apply { books = listOf(source) }
        val repo = BookRepository(dao)

        assertEquals(source, repo.findByUri(source.uri))
        assertEquals(source, repo.findById(source.id))
        assertNull(repo.findByUri("missing"))
        assertEquals(ReaderBook("b1", "content://a", "Title", "EPUB", 1f, "loc", 4), repo.findReaderBook(source.uri))
        assertNull(repo.findReaderBook("missing"))

        val values = listOf(source.copy(readProgress = -0.25f), source.copy(id = "nan", readProgress = Float.NaN))
        repo.insertBooks(values)
        assertEquals(listOf(0f, 0f), dao.inserted!!.map { it.readProgress })
        assertEquals(-0.25f, values.first().readProgress)
        assertTrue(values.last().readProgress.isNaN())

        repo.saveEpubProgress(source.uri, "chapter", 1.7f, 21)
        assertEquals(Triple("content://a", "chapter", 1f), dao.epubWrite)
        assertEquals(21L, dao.epubAt)
        repo.savePdfProgress(source.uri, 6, -1f, 22)
        assertEquals(listOf("content://a", 6, 0f), dao.pdfWrite)
        assertEquals(22L, dao.pdfAt)

        repo.markOpened(source.uri, 30)
        repo.updateFavorite(source.uri, true)
        repo.updateTitle(source.uri, "New")
        repo.updateCollection(source.uri, "Shelf")
        repo.moveCollection(8, -4)
        repo.renameCollection(8, "Renamed")
        repo.deleteCollection(8)
        repo.rebindUri("b1", "content://new")
        repo.deleteBook(source.uri)
        repo.completeCleanup(source.uri)
        assertEquals(listOf("content://a" to 30L), dao.opened)
        assertEquals(listOf(true), dao.favorites)
        assertEquals(listOf("New"), dao.titles)
        assertEquals(listOf("Shelf"), dao.collections)
        assertEquals(listOf(8L to -4), dao.moves)
        assertEquals(listOf(8L to "Renamed"), dao.renames)
        assertEquals(listOf(8L), dao.deletedCollections)
        assertEquals(listOf("b1" to "content://new"), dao.rebound)
        assertEquals(listOf("content://a"), dao.deletedBooks)
        assertEquals(listOf("content://a"), dao.completed)
        assertSame(dao.observeAll, repo.observeBooks())
        assertSame(dao.observeCollections, repo.observeCollections())
        assertSame(dao.observeAssignments, repo.observeBookCollectionNames())
        assertEquals(listOf("Shelf"), repo.collectionNamesForBook(source.uri))
        assertEquals(dao.collectionsList, repo.getCollections())
        assertEquals(listOf("content://pending"), repo.pendingCleanupUris())
    }

    @Test
    fun statsRepositoryDelegatesMutationsAndReturnsDaoFlowsAndLookup() = runBlocking {
        val dao = StatsDaoFake()
        val repo = ReadingStatsRepository(dao)
        val session = ReadingSessionEntity("2026-11-07", "book", 125)

        repo.save(session)
        assertSame(session, dao.saved)
        assertEquals(session, repo.find(session.dayKey, session.bookUri))
        assertSame(dao.dayFlow, repo.observeDurationForDay(session.dayKey))
        assertSame(dao.keysFlow, repo.observeReadingDayKeys())
        repo.addDuration(session.dayKey, session.bookUri, 0)
        assertEquals(listOf(Triple(session.dayKey, session.bookUri, 0L)), dao.added)
        repo.reset()
        assertTrue(dao.reset)
    }

    @Test
    fun backupRepositoryPassesThroughSnapshotBooksAndMergeReport() = runBlocking {
        val book = BookEntity(id = "b", uri = "uri", title = "Book", format = "PDF", importedAt = 1)
        val snapshot = LocalBackupSnapshot(
            books = listOf(book),
            annotations = emptyList(),
            sessions = emptyList(),
            collections = listOf(CollectionEntity(name = "Empty", normalizedName = "empty")),
        )
        val report = LocalBackupMergeReport(1, 2, 3, 4, 5, mapOf("old" to "new"))
        val state = BackupDaoState(snapshot, listOf(book), report)
        val dao = BackupDaoFake(state)
        val repo = LocalBackupRepository(dao)

        assertSame(snapshot, repo.snapshot())
        assertEquals(listOf(book), repo.allBooks())
        assertSame(report, repo.merge(snapshot))
        assertSame(snapshot, state.merged)
    }

    private class BookDaoFake(state: BookDaoState = BookDaoState()) :
        BookDao by proxy({ name, args ->
            state.dispatch(name, args)
        }) {
        private val backing = state
        var books: List<BookEntity>
            get() = backing.books
            set(value) {
                backing.books = value
            }
        val inserted get() = backing.inserted
        val epubWrite get() = backing.epubWrite
        val epubAt get() = backing.epubAt
        val pdfWrite get() = backing.pdfWrite
        val pdfAt get() = backing.pdfAt
        val opened get() = backing.opened
        val favorites get() = backing.favorites
        val titles get() = backing.titles
        val collections get() = backing.collections
        val moves get() = backing.moves
        val renames get() = backing.renames
        val deletedCollections get() = backing.deletedCollections
        val rebound get() = backing.rebound
        val deletedBooks get() = backing.deletedBooks
        val completed get() = backing.completed
        val observeAll get() = backing.observeAll
        val observeCollections get() = backing.observeCollections
        val observeAssignments get() = backing.observeAssignments
        val collectionsList get() = backing.collectionsList
    }

    private class BookDaoState {
        val observeAll = flowOf(emptyList<BookEntity>())
        val observeCollections = flowOf(emptyList<CollectionEntity>())
        val observeAssignments: Flow<List<BookCollectionName>> = flowOf(emptyList())
        var books = emptyList<BookEntity>()
        var inserted: List<BookEntity>? = null
        var epubWrite: Triple<String, String, Float>? = null
        var epubAt = 0L
        var pdfWrite: List<Any?>? = null
        var pdfAt = 0L
        val opened = mutableListOf<Pair<String, Long>>()
        val favorites = mutableListOf<Boolean>()
        val titles = mutableListOf<String>()
        val collections = mutableListOf<String>()
        val collectionsList = listOf(CollectionEntity(name = "Shelf", normalizedName = "shelf"))
        val moves = mutableListOf<Pair<Long, Int>>()
        val renames = mutableListOf<Pair<Long, String>>()
        val deletedCollections = mutableListOf<Long>()
        val rebound = mutableListOf<Pair<String, String>>()
        val deletedBooks = mutableListOf<String>()
        val completed = mutableListOf<String>()

        fun dispatch(name: String, args: Array<out Any?>): Any? = when (name) {
            "observeAll", "observeCollections", "observeBookCollectionNames",
            "findByUri", "findById", "collectionNamesForBook", "getCollections",
            "pendingBookCleanupUris",
            -> dispatchRead(name, args)
            "insertAll", "markOpened", "saveEpubProgress", "savePdfProgress",
            "updateFavorite", "updateTitle", "updateCollection",
            -> dispatchBookWrite(name, args)
            "moveCollection", "renameCollection", "deleteCollection" -> dispatchCollectionWrite(name, args)
            "rebindUri", "deleteByUri", "completeBookCleanup" -> dispatchCleanup(name, args)
            else -> error("Unexpected BookDao call: $name")
        }

        private fun dispatchRead(name: String, args: Array<out Any?>): Any? = when (name) {
            "observeAll" -> observeAll
            "observeCollections" -> observeCollections
            "observeBookCollectionNames" -> observeAssignments
            "findByUri" -> books.firstOrNull { it.uri == args[0] }
            "findById" -> books.firstOrNull { it.id == args[0] }
            "collectionNamesForBook" -> listOf("Shelf")
            "getCollections" -> collectionsList
            "pendingBookCleanupUris" -> listOf("content://pending")
            else -> error("Unexpected BookDao read: $name")
        }

        private fun dispatchBookWrite(name: String, args: Array<out Any?>): Any? = when (name) {
            "insertAll" -> {
                inserted = args[0] as List<BookEntity>
                Unit
            }
            "markOpened" -> {
                opened += (args[0] as String) to (args[1] as Long)
                Unit
            }
            "saveEpubProgress" -> {
                epubWrite = Triple(args[0] as String, args[1] as String, args[2] as Float)
                epubAt = args[3] as Long
                Unit
            }
            "savePdfProgress" -> {
                pdfWrite = listOf(args[0], args[1], args[2])
                pdfAt = args[3] as Long
                Unit
            }
            "updateFavorite" -> {
                favorites += args[1] as Boolean
                Unit
            }
            "updateTitle" -> {
                titles += args[1] as String
                Unit
            }
            "updateCollection" -> {
                collections += args[1] as String
                Unit
            }
            else -> error("Unexpected BookDao write: $name")
        }

        private fun dispatchCollectionWrite(name: String, args: Array<out Any?>): Any? = when (name) {
            "moveCollection" -> {
                moves += (args[0] as Long) to (args[1] as Int)
                Unit
            }
            "renameCollection" -> {
                renames += (args[0] as Long) to (args[1] as String)
                Unit
            }
            "deleteCollection" -> {
                deletedCollections += args[0] as Long
                Unit
            }
            else -> error("Unexpected BookDao collection write: $name")
        }

        private fun dispatchCleanup(name: String, args: Array<out Any?>): Any? = when (name) {
            "rebindUri" -> {
                rebound += (args[0] as String) to (args[1] as String)
                true
            }
            "deleteByUri" -> {
                deletedBooks += args[0] as String
                Unit
            }
            "pendingBookCleanupUris" -> listOf("content://pending")
            "completeBookCleanup" -> {
                completed += args[0] as String
                Unit
            }
            else -> error("Unexpected BookDao cleanup: $name")
        }
    }

    private class StatsDaoFake(state: StatsDaoState = StatsDaoState()) :
        ReadingSessionDao by proxy({ name, args ->
            state.dispatch(name, args)
        }) {
        private val backing = state
        val saved get() = backing.saved
        val dayFlow get() = backing.dayFlow
        val keysFlow get() = backing.keysFlow
        val added get() = backing.added
        val reset get() = backing.reset
    }

    private class StatsDaoState {
        var saved: ReadingSessionEntity? = null
        val dayFlow = flowOf(125L)
        val keysFlow = flowOf(listOf("2026-11-07"))
        val added = mutableListOf<Triple<String, String, Long>>()
        var reset = false

        fun dispatch(name: String, args: Array<out Any?>): Any? = when (name) {
            "upsert" -> {
                saved = args[0] as ReadingSessionEntity
                Unit
            }
            "find" -> saved?.takeIf { it.dayKey == args[0] && it.bookUri == args[1] }
            "observeDurationForDay" -> dayFlow
            "observeReadingDayKeys" -> keysFlow
            "addDuration" -> {
                added += Triple(args[0] as String, args[1] as String, args[2] as Long)
                Unit
            }
            "deleteAll" -> {
                reset = true
                Unit
            }
            else -> error("Unexpected ReadingSessionDao call: $name")
        }
    }

    private class BackupDaoFake(state: BackupDaoState) :
        LocalBackupDao by proxy({ name, args ->
            state.dispatch(name, args)
        })

    private class BackupDaoState(
        private val snapshot: LocalBackupSnapshot,
        private val books: List<BookEntity>,
        private val report: LocalBackupMergeReport,
    ) {
        var merged: LocalBackupSnapshot? = null

        fun dispatch(name: String, args: Array<out Any?>): Any? = when (name) {
            "snapshot" -> snapshot
            "allBooks" -> books
            "merge" -> {
                merged = args[0] as LocalBackupSnapshot
                report
            }
            else -> error("Unexpected LocalBackupDao call: $name")
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                when (method.name) {
                    "equals" -> false
                    "hashCode" -> System.identityHashCode(method)
                    "toString" -> "DAO fake"
                    else -> call(method.name, args ?: emptyArray())
                }
            } as T
    }
}
