package pe.lee.app.ui.library

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinueReadingCatalogTest {
    @Test
    fun continuesLatestIncompleteBooksAcrossFormatsEvenWithAnActiveFilter() {
        val epub = book("EPUB", 0.5f, 10L)
        val pdf = book("PDF", 0.25f, 20L)
        val books = listOf(epub, pdf, book("Audio", 0.4f, 30L), book("EPUB", 1f, 40L))
        val catalog = computeCatalog(
            books,
            "",
            LibraryFilter.PDF,
            null,
            LibrarySortMode.LAST_READ,
            naturalOrder(),
        )

        assertEquals(listOf(pdf), catalog.visible)
        assertEquals(listOf(pdf, epub), catalog.continueReading)
    }

    private fun book(format: String, progress: Float, openedAt: Long) = LocalBook(
        id = openedAt.toString(),
        uri = Uri.parse("content://lee.pe/books/$openedAt"),
        title = "Book $openedAt",
        format = format,
        isFavorite = false,
        importedAt = openedAt,
        lastOpenedAt = openedAt,
        readProgress = progress,
        collectionId = null,
        offlineCopyUri = null,
    )
}
