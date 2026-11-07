package pe.lee.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pe.lee.app.data.local.ReadingAnnotationDao
import pe.lee.app.data.local.ReadingAnnotationEntity
import pe.lee.core.domain.ReaderAnnotation

class AnnotationRepositoryTest {
    @Test
    fun rawRepositoryMethodsDelegateAndPreserveEntityFields() = runBlocking {
        val dao = InMemoryAnnotations()
        val repository = AnnotationRepository(dao)
        val entity = ReadingAnnotationEntity(
            id = "highlight", bookUri = "content://books/pdf", kind = "HIGHLIGHT",
            locatorJson = "{\"page\":3}", selectedText = "quoted", noteText = "note",
            colorArgb = 0x123456, createdAt = 12L, updatedAt = 19L,
        )
        repository.save(entity)
        assertEquals(listOf(entity), repository.getForBook(entity.bookUri))
        assertEquals(listOf(entity), repository.observeForBook(entity.bookUri).first())
        assertEquals(emptyList<ReadingAnnotationEntity>(), repository.getForBook("elsewhere"))
        repository.updateNote(entity.id, null, 24L)
        repository.updateColor(entity.id, -1, 25L)
        assertEquals(
            entity.copy(noteText = null, colorArgb = -1, updatedAt = 25L),
            repository.getForBook(entity.bookUri).single(),
        )
        repository.delete(entity.id)
        assertEquals(emptyList<ReadingAnnotationEntity>(), repository.observeForBook(entity.bookUri).first())
    }

    @Test
    fun bookmarkCanBeSavedObservedEditedAndDeleted() = runBlocking {
        val repository = AnnotationRepository(InMemoryAnnotations())
        val bookmark = ReaderAnnotation(
            id = "bookmark",
            bookUri = "content://books/epub",
            kind = "BOOKMARK",
            locatorJson = """{"href":"chapter.xhtml"}""",
            createdAt = 10L,
        )
        repository.saveReaderAnnotation(bookmark)
        assertEquals(listOf(bookmark), repository.getReaderAnnotations(bookmark.bookUri))
        assertEquals(emptyList<ReaderAnnotation>(), repository.getReaderAnnotations("content://books/other"))

        repository.updateReaderAnnotationNote(bookmark.id, "Releer", 20L)
        repository.updateReaderAnnotationColor(bookmark.id, 123, 30L)
        assertEquals(
            listOf(bookmark.copy(noteText = "Releer", colorArgb = 123, updatedAt = 30L)),
            repository.observeReaderAnnotations(bookmark.bookUri).first(),
        )

        repository.deleteReaderAnnotation(bookmark.id)
        assertEquals(emptyList<ReaderAnnotation>(), repository.observeReaderAnnotations(bookmark.bookUri).first())
    }

    private class InMemoryAnnotations : ReadingAnnotationDao {
        private val rows = MutableStateFlow(emptyList<ReadingAnnotationEntity>())

        override fun observeByBook(bookUri: String) = rows.map { entries -> entries.filter { it.bookUri == bookUri } }

        override suspend fun getByBook(bookUri: String) = rows.value.filter { it.bookUri == bookUri }

        override suspend fun upsert(annotation: ReadingAnnotationEntity) {
            rows.value = rows.value.filterNot { it.id == annotation.id } + annotation
        }

        override suspend fun updateNote(id: String, noteText: String?, updatedAt: Long) {
            rows.value = rows.value.map { if (it.id == id) it.copy(noteText = noteText, updatedAt = updatedAt) else it }
        }

        override suspend fun updateColor(id: String, colorArgb: Int, updatedAt: Long) {
            rows.value = rows.value.map {
                if (it.id == id) it.copy(colorArgb = colorArgb, updatedAt = updatedAt) else it
            }
        }

        override suspend fun deleteById(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }
}
