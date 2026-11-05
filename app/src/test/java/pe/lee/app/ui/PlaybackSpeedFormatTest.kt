package pe.lee.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedFormatTest {
    @Test fun formatsFractionalSpeedWithPeruvianDecimalSeparator() {
        assertEquals("1.25", formatPlaybackSpeed(1.25f))
    }

    @Test fun omitsUnnecessaryFractionForWholeSpeed() {
        assertEquals("1", formatPlaybackSpeed(1f))
    }
}
