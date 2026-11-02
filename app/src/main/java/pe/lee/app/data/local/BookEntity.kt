package pe.lee.app.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "library_books")
data class BookEntity(
    @PrimaryKey
    val uri: String,
    val title: String,
    val format: String,
    val importedAt: Long,
    val lastOpenedAt: Long? = null,
    val epubLocator: String? = null,
    val readProgress: Float = 0f,
    val pdfPageIndex: Int = 0,
    val isFavorite: Boolean = false,
    val collectionId: String? = null
)
