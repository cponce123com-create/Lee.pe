package pe.lee.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SpanishVoiceRegionTest {
    @Test fun parsesRegionInsteadOfMatchingCountryLettersAnywhere() {
        assertEquals("ES", spanishVoiceRegion("es-ES"))
        assertEquals("MX", spanishVoiceRegion("es-MX"))
        assertEquals("PE", spanishVoiceRegion("es-PE"))
        assertEquals("AR", spanishVoiceRegion("es_AR"))
        assertEquals("US", spanishVoiceRegion("es-US"))
    }

    @Test fun languageWithoutRegionHasNoCountryLabel() {
        assertEquals("", spanishVoiceRegion("es"))
    }
}
