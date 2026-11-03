package pe.lee.core.domain

/** Small reader-facing book model, independent of Room and Android types. */
data class ReaderBook(
    val id: String,
    val uri: String,
    val title: String,
    val format: String,
    val readProgress: Float,
    val epubLocator: String?,
    val pdfPageIndex: Int
)

/** Operations the EPUB and PDF readers need from the local book store. */
interface ReaderBookRepository {
    suspend fun findReaderBook(uri: String): ReaderBook?
    suspend fun markOpened(uri: String, at: Long)
    suspend fun saveEpubProgress(uri: String, locator: String, progress: Float, at: Long)
    suspend fun savePdfProgress(uri: String, page: Int, progress: Float, at: Long)
}
