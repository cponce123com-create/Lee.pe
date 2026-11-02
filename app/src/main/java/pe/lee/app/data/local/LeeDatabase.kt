package pe.lee.app.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL

@Database(
    entities = [BookEntity::class, ReadingAnnotationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class LeeDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingAnnotationDao(): ReadingAnnotationDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.executeSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reading_annotations` (
                        `id` TEXT NOT NULL,
                        `bookUri` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `locatorJson` TEXT NOT NULL,
                        `selectedText` TEXT,
                        `noteText` TEXT,
                        `colorArgb` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                connection.executeSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reading_annotations_bookUri_createdAt` " +
                        "ON `reading_annotations` (`bookUri`, `createdAt`)"
                )
            }
        }

        @Volatile
        private var instance: LeeDatabase? = null

        fun get(context: Context): LeeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<LeeDatabase>(
                    context.applicationContext,
                    "lee_pe_library.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
