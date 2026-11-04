package pe.lee.core.domain

import kotlinx.coroutines.flow.Flow

/** Reader-facing annotation data without Room annotations or Android dependencies. */
data class ReaderAnnotation(
    val id: String,
    val bookUri: String,
    val kind: String,
    val locatorJson: String,
    val selectedText: String? = null,
    val noteText: String? = null,
    val colorArgb: Int? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

/** Local annotation persistence used by readers; operations never require a network connection. */
interface ReaderAnnotationRepository {
    fun observeReaderAnnotations(bookUri: String): Flow<List<ReaderAnnotation>>
    suspend fun getReaderAnnotations(bookUri: String): List<ReaderAnnotation>
    suspend fun saveReaderAnnotation(annotation: ReaderAnnotation)
    suspend fun updateReaderAnnotationNote(id: String, note: String?, at: Long)
    suspend fun updateReaderAnnotationColor(id: String, color: Int, at: Long)
    suspend fun deleteReaderAnnotation(id: String)
}
