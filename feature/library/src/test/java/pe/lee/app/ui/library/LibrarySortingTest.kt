package pe.lee.app.ui.library

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Spanish alphabetical order of the catalog. Raw UTF-16 comparison
 * puts "Á" (0xC1) and "Ñ" (0xD1) after "z", which reads as a broken index in
 * every Spanish library.
 */
class LibrarySortingTest {
    private val comparator = libraryTitleComparator(Locale.forLanguageTag("es-PE"))

    private fun sorted(vararg titles: String): List<String> = titles.toList().sortedWith(comparator)

    @Test
    fun accentsAndEnyeSortLikeASpanishIndex() {
        assertEquals(
            listOf("Ángel", "Buenos Aires", "Nube", "Ñandú", "Zorro"),
            sorted("Zorro", "Ángel", "Nube", "Ñandú", "Buenos Aires"),
        )
    }

    @Test
    fun uppercaseDoesNotGoToTheEndOfTheList() {
        // Ñ is its own letter between N and O in Spanish, not after Z.
        val titles = sorted("zorro", "Ángel", "ópera", "Osa", "Ñandú")
        assertEquals(listOf("Ángel", "Ñandú", "ópera", "Osa", "zorro"), titles)
    }

    @Test
    fun caseDoesNotSplitTheList() {
        val titles = sorted("bosque", "Ángel", "alborada")
        assertEquals(listOf("alborada", "Ángel", "bosque"), titles)
    }

    @Test
    fun naiveStringOrderWouldBeWrong() {
        val titles = listOf("Zorro", "Ángel", "Ñandú")
        val naive = titles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        assertTrue(
            "la comparación UTF-16 coloca Ángel después de Zorro",
            naive.indexOf("Ángel") > naive.indexOf("Zorro"),
        )
        assertEquals("Ángel", sorted("Zorro", "Ángel", "Ñandú").first())
    }
}
