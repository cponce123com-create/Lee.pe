package pe.lee.app.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "reading_annotations",
    indices = [Index(value = ["bookUri", "createdAt"])]
)
data class ReadingAnnotationEntity(
    @PrimaryKey
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
