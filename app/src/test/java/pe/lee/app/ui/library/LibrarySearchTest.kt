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

    @Test fun everyQueryWordMustMatch() {
        assertFalse(matchesLibraryQuery("Donde la vida vuelve", "EPUB", null,
            normalizedLibraryText("vida desaparecida")))
    }
}
