package pe.lee.app.data.local

import androidx.room3.Entity

@Entity(
    tableName = "reading_sessions",
    primaryKeys = ["dayKey", "bookUri"],
)
data class ReadingSessionEntity(val dayKey: String, val bookUri: String, val durationSeconds: Long)
