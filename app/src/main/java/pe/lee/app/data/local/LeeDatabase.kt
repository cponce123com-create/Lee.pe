package pe.lee.app.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

@Database(
    entities = [BookEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LeeDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: LeeDatabase? = null

        fun get(context: Context): LeeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<LeeDatabase>(
                    context.applicationContext,
                    "lee_pe_library.db"
                ).build().also { instance = it }
            }
    }
}
