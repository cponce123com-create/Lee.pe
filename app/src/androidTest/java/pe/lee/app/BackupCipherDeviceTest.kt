package pe.lee.app

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pe.lee.core.domain.LocalBackupCipher

/** Exercise the installed Android crypto provider, not only the desktop JCA. */
@RunWith(AndroidJUnit4::class)
class BackupCipherDeviceTest {
    @Test
    fun unicodeBackupRoundTripsWithTheDeviceProvider() {
        val original = "notas españolas 📚".toByteArray()
        val encrypted = LocalBackupCipher.encrypt(original, "Contraseña-📚-2026")
        assertEquals(2, encrypted[8].toInt())
        assertArrayEquals(original, LocalBackupCipher.decrypt(encrypted, "Contraseña-📚-2026"))
    }

    @Test
    fun deviceCanStillOpenTheIndependentLegacyFixture() {
        val fixture = Base64.decode(
            "TEVFUEVCS1ABAAECAwQFBgcICQoLDA0ODwABAgMEBQYHCAkKC3wp2I54MMPivNSF/z8/tz7xLQNYX1sfxO+rlV/+U88UZInMivZORpryJd5cDw==",
            Base64.DEFAULT,
        )
        assertArrayEquals(
            """{"notes":["legado español"]}""".toByteArray(),
            LocalBackupCipher.decrypt(fixture, "Contraseña-📚-2026"),
        )
    }
}
