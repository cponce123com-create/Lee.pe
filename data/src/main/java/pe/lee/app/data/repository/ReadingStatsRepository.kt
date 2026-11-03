package pe.lee.app.data.repository

import pe.lee.app.data.local.ReadingSessionDao
import pe.lee.app.data.local.ReadingSessionEntity
import pe.lee.core.domain.ReadingStatisticsRepository as ReadingStatisticsContract
import kotlinx.coroutines.flow.Flow

class ReadingStatsRepository(private val dao: ReadingSessionDao) : ReadingStatisticsContract {
    override suspend fun addDuration(day: String, bookUri: String, seconds: Long) =
        dao.addDuration(day, bookUri, seconds)
    suspend fun save(session: ReadingSessionEntity) = dao.upsert(session)
    suspend fun find(day: String, uri: String) = dao.find(day, uri)
    override fun observeDurationForDay(day: String): Flow<Long> = dao.observeDurationForDay(day)
    override fun observeReadingDayKeys(): Flow<List<String>> = dao.observeReadingDayKeys()
    override suspend fun reset() = dao.deleteAll()
}
