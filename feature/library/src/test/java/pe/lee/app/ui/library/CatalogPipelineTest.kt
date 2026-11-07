package pe.lee.app.ui.library

import android.net.Uri
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog filter used to live inline in a composable, where it could not be
 * tested. It is now a pure function (P-06).
 */
class CatalogPipelineTest {
    private fun matches(
        format: String = "EPUB",
        progress: Float = 0f,
        favorite: Boolean = false,
        offline: Boolean = false,
        collections: List<String> = emptyList(),
        selectedCollection: String? = null,
        filter: LibraryFilter = LibraryFilter.ALL,
    ): Boolean = matchesCatalogFilter(
        format = format,
        readProgress = progress,
        isFavorite = favorite,
        hasOfflineCopy = offline,
        collectionNames = collections,
        selectedCollection = selectedCollection,
        filter = filter,
    )

    private fun book(
        id: String,
        title: String = id,
        format: String = "EPUB",
        progress: Float = 0f,
        lastOpened: Long? = null,
        imported: Long = 0,
        favorite: Boolean = false,
        offline: Boolean = false,
        collections: List<String> = emptyList(),
    ) = LocalBook(id, Uri.EMPTY, title, format, favorite, imported, lastOpened, progress, null,
        if (offline) Uri.EMPTY else null, collections)

    @Test
    fun toReadExcludesAudiobooksAndStartedBooks() {
        assertTrue(matches(progress = 0f, filter = LibraryFilter.TO_READ))
        assertFalse(matches(progress = 0.3f, filter = LibraryFilter.TO_READ))
        assertFalse(matches(format = "Audio", progress = 0f, filter = LibraryFilter.TO_READ))
    }

    @Test
    fun inProgressOnlyIncludesBooksBetweenZeroAndOne() {
        assertTrue(matches(progress = 0.3f, filter = LibraryFilter.IN_PROGRESS))
        assertFalse(matches(progress = 0f, filter = LibraryFilter.IN_PROGRESS))
        assertFalse(matches(progress = 1f, filter = LibraryFilter.IN_PROGRESS))
        assertFalse(matches(format = "Audio", progress = 0.5f, filter = LibraryFilter.IN_PROGRESS))
    }

    @Test
    fun completedRequiresProgressOne() {
        assertTrue(matches(progress = 1f, filter = LibraryFilter.COMPLETED))
        assertFalse(matches(progress = 0.99f, filter = LibraryFilter.COMPLETED))
    }

    @Test
    fun progressFiltersHandleOutOfRangeAndNanValuesWithoutTreatingAudioAsBooks() {
        assertTrue(matches(progress = -0.2f, filter = LibraryFilter.TO_READ))
        assertFalse(matches(progress = Float.NaN, filter = LibraryFilter.TO_READ))
        assertFalse(matches(progress = Float.NaN, filter = LibraryFilter.IN_PROGRESS))
        assertTrue(matches(progress = 1.2f, filter = LibraryFilter.COMPLETED))
        assertFalse(matches(format = "Audio", progress = 1.2f, filter = LibraryFilter.COMPLETED))
    }

    @Test
    fun offlineCopyAndFavoriteFiltersUseTheirFlags() {
        assertTrue(matches(offline = true, filter = LibraryFilter.OFFLINE_COPY))
        assertFalse(matches(offline = false, filter = LibraryFilter.OFFLINE_COPY))
        assertTrue(matches(favorite = true, filter = LibraryFilter.FAVORITES))
        assertFalse(matches(favorite = false, filter = LibraryFilter.FAVORITES))
    }

    @Test
    fun formatFiltersMatchOnlyTheirFormat() {
        assertTrue(matches(format = "EPUB", filter = LibraryFilter.EPUB))
        assertFalse(matches(format = "PDF", filter = LibraryFilter.EPUB))
        assertTrue(matches(format = "PDF", filter = LibraryFilter.PDF))
        assertTrue(matches(format = "Audio", filter = LibraryFilter.AUDIO))
    }

    @Test
    fun selectedCollectionIsMandatoryWhenSet() {
        assertTrue(matches(collections = listOf("Novela", "Perú"), selectedCollection = "Novela"))
        assertTrue(matches(collections = listOf("Novela", "Perú"), selectedCollection = "Perú"))
        assertFalse(matches(collections = listOf("Perú"), selectedCollection = "Novela"))
        assertFalse(matches(collections = emptyList(), selectedCollection = "Novela"))
        assertTrue(matches(collections = emptyList(), selectedCollection = null))
    }

    @Test
    fun catalogSortModesOrderVisibleBooksAndKeepInputOrderForTies() {
        val books = listOf(
            book("c", "Nube", progress = .5f, lastOpened = 10, imported = 3),
            book("a", "Árbol", progress = .8f, lastOpened = 20, imported = 2),
            book("b", "Bosque", progress = .8f, lastOpened = 20, imported = 3),
        )
        val comparator = libraryTitleComparator(Locale.forLanguageTag("es-PE"))
        fun ordered(mode: LibrarySortMode) = computeCatalog(
            books,
            "",
            LibraryFilter.ALL,
            null,
            mode,
            comparator,
        ).ordered.map { it.id }

        assertEquals(listOf("c", "a", "b"), ordered(LibrarySortMode.CUSTOM))
        assertEquals(listOf("a", "b", "c"), ordered(LibrarySortMode.TITLE_ASC))
        assertEquals(listOf("c", "b", "a"), ordered(LibrarySortMode.TITLE_DESC))
        assertEquals(listOf("a", "b", "c"), ordered(LibrarySortMode.LAST_READ))
        assertEquals(listOf("c", "b", "a"), ordered(LibrarySortMode.IMPORT_DATE))
        assertEquals(listOf("a", "b", "c"), ordered(LibrarySortMode.PROGRESS))
    }

    @Test
    fun continueReadingIsIndependentOfCatalogFilterAndTakesEightMostRecentEligibleBooks() {
        val books = listOf(book("audio", format = "Audio", progress = .5f, lastOpened = 1000)) +
            (0..8).map { book("r$it", progress = .2f, lastOpened = it.toLong()) } +
            listOf(book("unread", lastOpened = 2000), book("done", progress = 1f, lastOpened = 3000))

        val result = computeCatalog(books, "r", LibraryFilter.EPUB, "no-matching-collection", LibrarySortMode.CUSTOM,
            libraryTitleComparator(Locale.ROOT))

        assertTrue(result.visible.isEmpty())
        assertEquals(listOf("r8", "r7", "r6", "r5", "r4", "r3", "r2", "r1"), result.continueReading.map { it.id })
    }

    @Test
    fun searchMatchesNormalizedTitleFormatAndCollectionTerms() {
        val books = listOf(
            book("one", title = "Árbol Azul", collections = listOf("Cuentos")),
            book("two", title = "Árbol"),
        )
        val result = computeCatalog(
            books,
            "CUENTOS arbol",
            LibraryFilter.ALL,
            null,
            LibrarySortMode.CUSTOM,
            libraryTitleComparator(Locale.ROOT),
        )
        assertEquals(listOf("one"), result.visible.map { it.id })
    }
}
