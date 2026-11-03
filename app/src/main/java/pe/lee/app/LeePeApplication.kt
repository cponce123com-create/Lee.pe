package pe.lee.app

import android.app.Application
import pe.lee.app.data.local.LeeDatabase
import pe.lee.app.data.repository.AnnotationRepository
import pe.lee.app.data.repository.BookRepository
import pe.lee.app.data.repository.ReadingStatsRepository
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.data.repository.LocalBackupRepository

class LeePeApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
}

class AppContainer(context: Application) {
    val database = LeeDatabase.get(context)
    val settings = SettingsRepository(context)
    val books = BookRepository(database.bookDao())
    val annotations = AnnotationRepository(database.readingAnnotationDao())
    val readingStats = ReadingStatsRepository(database.readingSessionDao())
    val backup = LocalBackupRepository(database.localBackupDao())
    val viewModelFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppViewModelFactory(this) }
}
