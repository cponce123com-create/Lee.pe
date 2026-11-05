package pe.lee.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.lee.app.data.local.LeeDatabase
import pe.lee.app.data.repository.AnnotationRepository
import pe.lee.app.data.repository.BookRepository
import pe.lee.app.data.repository.LocalBackupRepository
import pe.lee.app.data.repository.ReadingStatsRepository
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.feature.sharecard.data.QuoteCardCacheCleaner

class LeePeApplication : Application() {
    private val startupScope = CoroutineScope(SupervisorJob())
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        startupScope.launch {
            runStartupCleanupOnIo {
                QuoteCardCacheCleaner.removeExpired(this@LeePeApplication)
            }
        }
    }
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
