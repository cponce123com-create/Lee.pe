package pe.lee.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlGeometryTest {
    private val count = (PAGE_CURL_COLUMNS + 1) * (PAGE_CURL_ROWS + 1)
    private val vertices = FloatArray(count * 2)
    private val back = BooleanArray(count)

    @Test
    fun sheetIsFlatAtRestAndLeavesViewportAtCompletion() {
        fillPageCurlMesh(vertices, back, 400f, 720f, 0f, true)
        assertEquals(0f, vertices.first(), .01f)
        assertEquals(400f, vertices[PAGE_CURL_COLUMNS * 2], .01f)
        assertFalse(back.any { it })

        fillPageCurlMesh(vertices, back, 400f, 720f, 1f, true)
        assertTrue((0 until count).all { vertices[it * 2] < 0f })
    }

    @Test
    fun intermediateSheetHasReturningTailAndMirrorsDirection() {
        fillPageCurlMesh(vertices, back, 400f, 720f, .55f, true)
        val forward = vertices.copyOf()
        assertTrue(back.any { it })
        assertTrue(back.any { !it })
        assertTrue(forward.all { it.isFinite() })
        assertTrue(forward[PAGE_CURL_COLUMNS * 2] < forward[(PAGE_CURL_COLUMNS - 1) * 2])

        fillPageCurlMesh(vertices, back, 400f, 720f, .55f, false)
        for (column in 0..PAGE_CURL_COLUMNS) {
            assertEquals(400f - forward[(PAGE_CURL_COLUMNS - column) * 2], vertices[column * 2], .01f)
        }
    }

    @Test
    fun shortSlowDragCancelsAndIntentionalFlingCompletes() {
        assertFalse(shouldCommitPageCurl(.1f, 1000f, 400))
        assertFalse(shouldCommitPageCurl(.3f, -800f, 400))
        assertTrue(shouldCommitPageCurl(.35f, 0f, 400))
        assertTrue(shouldCommitPageCurl(.2f, 600f, 400))
    }
}
