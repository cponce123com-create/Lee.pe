package pe.lee.app.data.repository

import pe.lee.app.data.local.ReadingSessionDao
import pe.lee.app.data.local.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

class ReadingStatsRepository(private val dao: ReadingSessionDao) {
    suspend fun addDuration(day: String, uri: String, seconds: Long) = dao.addDuration(day, uri, seconds)
    suspend fun save(session: ReadingSessionEntity) = dao.upsert(session)
    suspend fun find(day: String, uri: String) = dao.find(day, uri)
    fun observeDurationForDay(day: String): Flow<Long> = dao.observeDurationForDay(day)
    fun observeReadingDayKeys(): Flow<List<String>> = dao.observeReadingDayKeys()
    suspend fun reset() = dao.deleteAll()
}
