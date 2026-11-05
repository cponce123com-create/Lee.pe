package pe.lee.app.ui.reader

import java.io.FileNotFoundException
import java.io.IOException
import pe.lee.app.R

/** Providers and publication openers can wrap the underlying storage exception. */
internal fun readerOpenFailureMessage(error: Throwable): Int {
    val causes = generateSequence(error) { it.cause }.take(16).toList()
    return when {
        causes.any { it is SecurityException } -> R.string.epub_permission_lost
        causes.any { it is FileNotFoundException } -> R.string.epub_file_unavailable
        causes.any { it is IOException } -> R.string.epub_storage_read_failed
        else -> R.string.epub_open_failed
    }
}
