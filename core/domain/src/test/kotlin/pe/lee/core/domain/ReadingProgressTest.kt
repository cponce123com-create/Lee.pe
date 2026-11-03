package pe.lee.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProgressTest {
    @Test
    fun clampsValuesToValidReadingRange() {
        assertEquals(0f, ReadingProgress.from(-0.4f).fraction, 0f)
        assertEquals(1f, ReadingProgress.from(1.4f).fraction, 0f)
        assertEquals(0.36f, ReadingProgress.from(0.36f).fraction, 0f)
    }

    @Test
    fun replacesNonFiniteValuesWithZero() {
        assertEquals(0f, ReadingProgress.from(Float.NaN).fraction, 0f)
        assertEquals(0f, ReadingProgress.from(Float.POSITIVE_INFINITY).fraction, 0f)
        assertEquals(0f, ReadingProgress.from(Float.NEGATIVE_INFINITY).fraction, 0f)
    }

    @Test
    fun exposesPresentationState() {
        val progress = ReadingProgress.from(0.369f)
        assertEquals(36, progress.percentage)
        assertTrue(progress.isStarted)
        assertFalse(progress.isCompleted)
        assertTrue(ReadingProgress.from(1f).isCompleted)
    }

    @Test
    fun locatorProgressionWinsWhenAvailable() {
        assertEquals(0.25f, readingProgressOr(0.25, 0.9f), 0f)
    }

    @Test
    fun missingProgressionKeepsTheLastKnownValue() {
        assertEquals(0.42f, readingProgressOr(null, 0.42f), 0f)
        assertEquals(1f, readingProgressOr(null, 1f), 0f)
    }

    @Test
    fun invalidProgressionNeverResetsProgress() {
        assertEquals(0.6f, readingProgressOr(Double.NaN, 0.6f), 0f)
        assertEquals(0.6f, readingProgressOr(Double.POSITIVE_INFINITY, 0.6f), 0f)
        assertEquals(0.6f, readingProgressOr(Double.NEGATIVE_INFINITY, 0.6f), 0f)
    }

    @Test
    fun progressionIsClampedToTheReadingRange() {
        assertEquals(1f, readingProgressOr(1.7, 0.1f), 0f)
        assertEquals(0f, readingProgressOr(-0.3, 0.1f), 0f)
    }
}
