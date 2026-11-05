package pe.lee.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun runStartupCleanupOnIo(cleanup: () -> Unit) {
    withContext(Dispatchers.IO) { cleanup() }
}
