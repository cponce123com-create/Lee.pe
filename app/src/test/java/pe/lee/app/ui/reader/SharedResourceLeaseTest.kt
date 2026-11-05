package pe.lee.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SharedResourceLeaseTest {
    @Test
    fun stoppingVoiceDoesNotCloseTheBookStillDisplayedByTheReader() {
        var closes = 0
        val reader = SharedResourceLease("epub") { closes++ }
        val voice = reader.retain()
        voice.close()
        assertEquals(0, closes)
        assertEquals("epub", reader.resource)
        val resumedVoice = reader.retain()
        resumedVoice.close()
        assertEquals(0, closes)
        reader.close()
        assertEquals(1, closes)
    }

    @Test
    fun leavingTheReaderKeepsBackgroundNarrationAlive() {
        var closes = 0
        val reader = SharedResourceLease("epub") { closes++ }
        val voice = reader.retain()
        reader.close()
        assertEquals("epub", voice.resource)
        assertEquals(0, closes)
        voice.close()
        assertEquals(1, closes)
    }

    @Test
    fun releasingOwnersTwiceDoesNotCloseTheUnderlyingResourceTwice() {
        var closes = 0
        val reader = SharedResourceLease("epub") { closes++ }
        val voice = reader.retain()
        reader.close()
        reader.close()
        voice.close()
        voice.close()
        assertEquals(1, closes)
    }

    @Test
    fun closedOwnersCannotBeReusedWhileAnotherOwnerIsStillActive() {
        val reader = SharedResourceLease("epub") {}
        val voice = reader.retain()
        reader.close()
        assertThrows(IllegalStateException::class.java) { reader.retain() }
        assertThrows(IllegalStateException::class.java) { reader.resource }
        assertEquals("epub", voice.resource)
        voice.close()
    }

    @Test
    fun aFailedCloseIsNotRepeatedByALaterLifecycleCallback() {
        var attempts = 0
        val owner = SharedResourceLease("epub") {
            attempts++
            error("close failure")
        }
        assertThrows(IllegalStateException::class.java) { owner.close() }
        owner.close()
        assertEquals(1, attempts)
    }
}
