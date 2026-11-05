package pe.lee.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationFollowerTest {
    @Test
    fun followsWordsAcrossPagesInsteadOfReturningToTheStartOfALongSentence() {
        val visits = mutableListOf<String>()
        val follower = NarrationFollower<String>({ it }) {
            visits.add(it)
            true
        }

        follower.update(true, "sentence-page-1", "word-page-1")
        follower.update(true, "sentence-page-1", "word-page-2")
        follower.update(true, "sentence-page-1", "word-page-3")

        assertEquals(listOf("word-page-1", "word-page-2", "word-page-3"), visits)
    }

    @Test
    fun voicesWithoutWordRangesStillFollowTheCurrentSentence() {
        val visits = mutableListOf<String>()
        val follower = NarrationFollower<String>({ it }) {
            visits.add(it)
            true
        }
        follower.update(true, "sentence-1", null)
        follower.update(true, "sentence-2", null)
        assertEquals(listOf("sentence-1", "sentence-2"), visits)
    }

    @Test
    fun pausedNarrationDoesNotUndoAManualPageJump() {
        var visits = 0
        val follower = NarrationFollower<String>({ it }) {
            visits++
            true
        }
        assertFalse(follower.update(false, "old-page", "old-word"))
        assertFalse(follower.update(false, "new-page", "new-word"))
        assertEquals(0, visits)
    }

    @Test
    fun resumingRefocusesTheCurrentWordEvenWhenItsLocationDidNotChange() {
        val visits = mutableListOf<String>()
        val follower = NarrationFollower<String>({ it }) {
            visits.add(it)
            true
        }
        follower.update(true, "sentence", "word")
        follower.update(false, "sentence", "word")
        follower.update(true, "sentence", "word")
        assertEquals(listOf("word", "word"), visits)
    }

    @Test
    fun repeatedEngineEventsDoNotReloadTheSameLocation() {
        var visits = 0
        val follower = NarrationFollower<String>({ it }) {
            visits++
            true
        }
        repeat(8) { follower.update(true, "sentence", "word") }
        assertEquals(1, visits)
    }

    @Test
    fun navigationRejectedDuringLoadingIsRetried() {
        var attempts = 0
        val follower = NarrationFollower<String>({ it }) { ++attempts > 1 }
        assertFalse(follower.update(true, "sentence", "word"))
        assertTrue(follower.update(true, "sentence", "word"))
        assertFalse(follower.update(true, "sentence", "word"))
        assertEquals(2, attempts)
    }
}
