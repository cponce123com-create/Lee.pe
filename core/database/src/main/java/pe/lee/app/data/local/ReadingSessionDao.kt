package pe.lee.app.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions WHERE dayKey = :dayKey AND bookUri = :bookUri LIMIT 1")
    suspend fun find(dayKey: String, bookUri: String): ReadingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ReadingSessionEntity)

    @Transaction
    suspend fun addDuration(dayKey: String, bookUri: String, seconds: Long) {
        if (seconds <= 0) return
        val current = find(dayKey, bookUri)
        upsert(
            ReadingSessionEntity(
                dayKey = dayKey,
                bookUri = bookUri,
                durationSeconds = (current?.durationSeconds ?: 0L) + seconds
            )
        )
    }

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_sessions WHERE dayKey = :dayKey")
    fun observeDurationForDay(dayKey: String): Flow<Long>

    @Query("SELECT dayKey FROM reading_sessions WHERE durationSeconds > 0 GROUP BY dayKey ORDER BY dayKey DESC")
    fun observeReadingDayKeys(): Flow<List<String>>

    @Query("DELETE FROM reading_sessions")
    suspend fun deleteAll()
}
