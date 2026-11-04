package pe.lee.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.lee.app.data.local.ReadingAnnotationDao
import pe.lee.app.data.local.ReadingAnnotationEntity
import pe.lee.core.domain.ReaderAnnotation
import pe.lee.core.domain.ReaderAnnotationRepository

class AnnotationRepository(private val dao: ReadingAnnotationDao) : ReaderAnnotationRepository {
    fun observeForBook(uri: String): Flow<List<ReadingAnnotationEntity>> = dao.observeByBook(uri)
    suspend fun getForBook(uri: String) = dao.getByBook(uri)
    suspend fun save(annotation: ReadingAnnotationEntity) = dao.upsert(annotation)
    suspend fun updateNote(id: String, note: String?, at: Long) = dao.updateNote(id, note, at)
    suspend fun updateColor(id: String, color: Int, at: Long) = dao.updateColor(id, color, at)
    suspend fun delete(id: String) = dao.deleteById(id)

    override fun observeReaderAnnotations(bookUri: String): Flow<List<ReaderAnnotation>> =
        dao.observeByBook(bookUri).map { annotations -> annotations.map(ReadingAnnotationEntity::toDomain) }

    override suspend fun getReaderAnnotations(bookUri: String): List<ReaderAnnotation> =
        dao.getByBook(bookUri).map(ReadingAnnotationEntity::toDomain)

    override suspend fun saveReaderAnnotation(annotation: ReaderAnnotation) = dao.upsert(annotation.toEntity())

    override suspend fun updateReaderAnnotationNote(id: String, note: String?, at: Long) = dao.updateNote(id, note, at)

    override suspend fun updateReaderAnnotationColor(id: String, color: Int, at: Long) = dao.updateColor(id, color, at)

    override suspend fun deleteReaderAnnotation(id: String) = dao.deleteById(id)
}

private fun ReadingAnnotationEntity.toDomain() = ReaderAnnotation(
    id = id,
    bookUri = bookUri,
    kind = kind,
    locatorJson = locatorJson,
    selectedText = selectedText,
    noteText = noteText,
    colorArgb = colorArgb,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ReaderAnnotation.toEntity() = ReadingAnnotationEntity(
    id = id,
    bookUri = bookUri,
    kind = kind,
    locatorJson = locatorJson,
    selectedText = selectedText,
    noteText = noteText,
    colorArgb = colorArgb,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
