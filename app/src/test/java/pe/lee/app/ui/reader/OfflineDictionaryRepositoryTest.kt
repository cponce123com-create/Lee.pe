package pe.lee.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineDictionaryRepositoryTest {
    private val rows = listOf(
        "arbol\tárbol\ttree",
        "cafe\tcafé\tcoffee",
        "nino\tniño\tchild",
    )

    @Test
    fun lookupKeyIgnoresCaseAndSpanishAccents() {
        assertEquals("cafe\tcafé\tcoffee", findDictionaryRow(rows, "CAFÉ"))
        assertEquals("arbol\tárbol\ttree", findDictionaryRow(rows, "Árbol"))
        assertEquals("nino\tniño\tchild", findDictionaryRow(rows, "NINO"))
    }

    @Test
    fun lookupReturnsNullWhenTermIsMissing() {
        assertNull(findDictionaryRow(rows, "inexistente"))
    }

    @Test
    fun normalizationTrimsWhitespaceAndDoesNotUseDefaultLocale() {
        assertEquals("cafe", normalizeDictionaryTerm("  CAFÉ  "))
        assertEquals("i", normalizeDictionaryTerm("I"))
    }

    @Test
    fun normalizationRemovesCombiningMarksButKeepsPunctuation() {
        assertEquals("a!", normalizeDictionaryTerm(" A\u0301! "))
        assertEquals("n", normalizeDictionaryTerm("N\u20DD"))
    }

    @Test
    fun blankAndMarksOnlyTermsCannotMatch() {
        assertNull(findDictionaryRow(rows, " \t "))
        assertNull(findDictionaryRow(rows, "\u0301"))
    }

    @Test
    fun lookupUsesExactKeyRatherThanPrefix() {
        assertNull(findDictionaryRow(rows, "cafeteria"))
    }
}
