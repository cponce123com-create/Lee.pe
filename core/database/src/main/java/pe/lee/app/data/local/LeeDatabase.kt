package pe.lee.app.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        BookEntity::class,
        ReadingAnnotationEntity::class,
        ReadingSessionEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        PendingBookCleanupEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class LeeDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingAnnotationDao(): ReadingAnnotationDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun localBackupDao(): LocalBackupDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
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
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reading_annotations_bookUri_createdAt` " +
                        "ON `reading_annotations` (`bookUri`, `createdAt`)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reading_sessions` (
                        `dayKey` TEXT NOT NULL,
                        `bookUri` TEXT NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        PRIMARY KEY(`dayKey`, `bookUri`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_books_new` (
                        `id` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `format` TEXT NOT NULL,
                        `importedAt` INTEGER NOT NULL,
                        `lastOpenedAt` INTEGER,
                        `epubLocator` TEXT,
                        `readProgress` REAL NOT NULL,
                        `pdfPageIndex` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `collectionId` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT INTO `library_books_new` (
                        `id`, `uri`, `title`, `format`, `importedAt`, `lastOpenedAt`,
                        `epubLocator`, `readProgress`, `pdfPageIndex`, `isFavorite`, `collectionId`
                    )
                    SELECT `uri`, `uri`, `title`, `format`, `importedAt`, `lastOpenedAt`,
                           `epubLocator`, `readProgress`, `pdfPageIndex`, `isFavorite`, `collectionId`
                    FROM `library_books`
                    """.trimIndent(),
                )
                connection.execSQL("DROP TABLE `library_books`")
                connection.execSQL("ALTER TABLE `library_books_new` RENAME TO `library_books`")
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_library_books_uri` ON `library_books` (`uri`)",
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_normalizedName` " +
                        "ON `collections` (`normalizedName`)",
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `book_collections` (
                        `bookId` TEXT NOT NULL,
                        `collectionId` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`, `collectionId`),
                        FOREIGN KEY(`bookId`) REFERENCES `library_books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`collectionId`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_book_collections_bookId` ON `book_collections` (`bookId`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_book_collections_collectionId` ON `book_collections` (`collectionId`)",
                )
                connection.execSQL(
                    """
                    INSERT OR IGNORE INTO `collections` (`name`, `normalizedName`, `sortOrder`)
                    SELECT MIN(TRIM(`collectionId`) COLLATE NOCASE), LOWER(TRIM(`collectionId`)), 0
                    FROM `library_books`
                    WHERE `collectionId` IS NOT NULL AND TRIM(`collectionId`) <> ''
                    GROUP BY LOWER(TRIM(`collectionId`))
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    INSERT OR IGNORE INTO `book_collections` (`bookId`, `collectionId`)
                    SELECT books.`id`, collections.`id`
                    FROM `library_books` AS books
                    JOIN `collections` AS collections
                      ON collections.`normalizedName` = LOWER(TRIM(books.`collectionId`))
                    WHERE books.`collectionId` IS NOT NULL AND TRIM(books.`collectionId`) <> ''
                    """.trimIndent(),
                )
                connection.execSQL(
                    """
                    UPDATE `library_books`
                    SET `collectionId` = (
                        SELECT `collections`.`name` FROM `collections`
                        WHERE `collections`.`normalizedName` = LOWER(TRIM(`library_books`.`collectionId`))
                    )
                    WHERE `collectionId` IS NOT NULL AND TRIM(`collectionId`) <> ''
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """CREATE TABLE IF NOT EXISTS `pending_book_cleanup` (
                        `uri` TEXT NOT NULL,
                        PRIMARY KEY(`uri`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookUri` ON `reading_sessions` (`bookUri`)",
                )
            }
        }

        /** Supported migration chain for custom database builders and integration tests. */
        val ALL_MIGRATIONS get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        @Volatile
        private var instance: LeeDatabase? = null

        fun get(context: Context): LeeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder<LeeDatabase>(
                context.applicationContext,
                "lee_pe_library.db",
            )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                .also { instance = it }
        }
    }
}
