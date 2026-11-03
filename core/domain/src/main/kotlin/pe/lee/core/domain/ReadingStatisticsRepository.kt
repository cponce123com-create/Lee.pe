package pe.lee.core.domain

import kotlinx.coroutines.flow.Flow

/** Offline reading-time operations used by the library and the reading session recorder. */
interface ReadingStatisticsRepository {
    suspend fun addDuration(day: String, bookUri: String, seconds: Long)
    fun observeDurationForDay(day: String): Flow<Long>
    fun observeReadingDayKeys(): Flow<List<String>>
    suspend fun reset()
}
