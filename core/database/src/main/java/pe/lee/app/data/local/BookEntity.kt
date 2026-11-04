package pe.lee.app.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(tableName = "library_books", indices = [Index(value = ["uri"], unique = true)])
data class BookEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val title: String,
    val format: String,
    val importedAt: Long,
    val lastOpenedAt: Long? = null,
    val epubLocator: String? = null,
    val readProgress: Float = 0f,
    val pdfPageIndex: Int = 0,
    val isFavorite: Boolean = false,
    val collectionId: String? = null,
)
