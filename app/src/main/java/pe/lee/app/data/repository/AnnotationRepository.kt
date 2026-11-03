package pe.lee.app.data.repository

import pe.lee.app.data.local.ReadingAnnotationDao
import pe.lee.app.data.local.ReadingAnnotationEntity
import kotlinx.coroutines.flow.Flow

class AnnotationRepository(private val dao: ReadingAnnotationDao) {
    fun observeForBook(uri: String): Flow<List<ReadingAnnotationEntity>> = dao.observeByBook(uri)
    suspend fun getForBook(uri: String) = dao.getByBook(uri)
    suspend fun save(annotation: ReadingAnnotationEntity) = dao.upsert(annotation)
    suspend fun updateNote(id: String, note: String?, at: Long) = dao.updateNote(id, note, at)
    suspend fun updateColor(id: String, color: Int, at: Long) = dao.updateColor(id, color, at)
    suspend fun delete(id: String) = dao.deleteById(id)
}
