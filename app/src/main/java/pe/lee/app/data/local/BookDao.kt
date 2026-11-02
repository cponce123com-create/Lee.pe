package pe.lee.app.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM library_books ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BookEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(books: List<BookEntity>)

    @Query("SELECT * FROM library_books WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): BookEntity?

    @Query("UPDATE library_books SET lastOpenedAt = :openedAt WHERE uri = :uri")
    suspend fun markOpened(uri: String, openedAt: Long)

    @Query("DELETE FROM library_books WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)
}
