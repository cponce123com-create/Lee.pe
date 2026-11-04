package pe.lee.app.ui.library

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
        filter: LibraryFilter = LibraryFilter.ALL
    ): Boolean = matchesCatalogFilter(
        format = format,
        readProgress = progress,
        isFavorite = favorite,
        hasOfflineCopy = offline,
        collectionNames = collections,
        selectedCollection = selectedCollection,
        filter = filter
    )

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
}
