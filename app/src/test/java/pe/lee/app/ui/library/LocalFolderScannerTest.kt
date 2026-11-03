package pe.lee.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalFolderScannerTest {
    @Test fun detectsBooksByExtension() {
        assertEquals("EPUB", LocalFolderScanner.formatOf("Donde la vida vuelve a empezar.EPUB"))
        assertEquals("PDF", LocalFolderScanner.formatOf("  informe final.pdf"))
        assertEquals("Audio", LocalFolderScanner.formatOf("Historia.M4B"))
        assertEquals("Audio", LocalFolderScanner.formatOf("Narracion.flac"))
    }

    @Test fun rejectsUnrelatedAndMisleadingFiles() {
        assertNull(LocalFolderScanner.formatOf("portada.jpg"))
        assertNull(LocalFolderScanner.formatOf("manual.pdf.exe"))
        assertNull(LocalFolderScanner.formatOf("archivo.zip"))
        assertNull(LocalFolderScanner.formatOf("sin_extension"))
    }

    @Test fun scannerHasBoundsForMaliciousFolderTrees() {
        assertEquals(20_000, LocalFolderScanner.MAX_FILES)
        assertEquals(2_000, LocalFolderScanner.MAX_DIRECTORIES)
    }
}
