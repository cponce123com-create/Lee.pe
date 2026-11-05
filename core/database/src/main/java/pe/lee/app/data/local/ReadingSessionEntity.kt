package pe.lee.app.data.local

import androidx.room3.Entity
import androidx.room3.Index

@Entity(
    tableName = "reading_sessions",
    indices = [Index(value = ["bookUri"])],
    primaryKeys = ["dayKey", "bookUri"],
)
data class ReadingSessionEntity(val dayKey: String, val bookUri: String, val durationSeconds: Long)
