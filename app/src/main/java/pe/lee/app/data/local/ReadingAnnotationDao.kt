package pe.lee.app.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingAnnotationDao {
    @Query("SELECT * FROM reading_annotations WHERE bookUri = :bookUri ORDER BY createdAt DESC")
    fun observeByBook(bookUri: String): Flow<List<ReadingAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: ReadingAnnotationEntity)

    @Query("UPDATE reading_annotations SET noteText = :noteText, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNote(id: String, noteText: String?, updatedAt: Long)

    @Query("UPDATE reading_annotations SET colorArgb = :colorArgb, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateColor(id: String, colorArgb: Int, updatedAt: Long)

    @Query("DELETE FROM reading_annotations WHERE id = :id")
    suspend fun deleteById(id: String)
}
