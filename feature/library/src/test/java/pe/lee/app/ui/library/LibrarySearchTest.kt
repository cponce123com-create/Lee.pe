package pe.lee.app.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchTest {
    @Test fun searchIgnoresCaseAndSpanishAccents() {
        assertEquals("garcia marquez", normalizedLibraryText("  GARCÍA   MÁRQUEZ  "))
        assertTrue(matchesLibraryQuery("García Márquez", "EPUB", null, normalizedLibraryText("garcia")))
    }

    @Test fun searchMatchesWordsInAnyOrder() {
        assertTrue(matchesLibraryQuery("Cien años de soledad", "EPUB", "Favoritos",
            normalizedLibraryText("soledad cien")))
    }

    @Test fun searchCanMatchCollectionAndFormat() {
        assertTrue(matchesLibraryQuery("Mi novela", "PDF", "Selva Central",
            normalizedLibraryText("selva pdf")))
        assertFalse(matchesLibraryQuery("Mi novela", "EPUB", "Selva Central",
            normalizedLibraryText("selva pdf")))
    }

    @Test fun emptySearchMatchesEverything() {
        assertTrue(matchesLibraryQuery("Libro", "EPUB", null, normalizedLibraryText("   ")))
    }

    @Test fun indexedSearchRemainsCorrectWithTenThousandLocalBooks() {
        val library = (1..10_000).map { index ->
            normalizedLibraryText("Novela número $index EPUB Literatura")
        }
        val target = normalizedLibraryText("numero 9999")
        val matches = library.count { matchesNormalizedLibraryQuery(it, target) }
        assertEquals(1, matches)
        assertTrue(matchesNormalizedLibraryQuery(
            normalizedLibraryText("García Márquez EPUB Clásicos"),
            normalizedLibraryText("marquez clasicos")
        ))
    }

    @Test fun everyQueryWordMustMatch() {
        assertFalse(matchesLibraryQuery("Donde la vida vuelve", "EPUB", null,
            normalizedLibraryText("vida desaparecida")))
    }
}
