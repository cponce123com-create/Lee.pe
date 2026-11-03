package pe.lee.app.data.local

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** Durable outbox for app-private data that must be removed after a library entry is deleted. */
@Entity(tableName = "pending_book_cleanup")
data class PendingBookCleanupEntity(
    @PrimaryKey val uri: String
)
