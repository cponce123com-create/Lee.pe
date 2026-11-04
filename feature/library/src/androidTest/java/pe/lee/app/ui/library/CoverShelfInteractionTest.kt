package pe.lee.app.ui.library

import android.net.Uri
import android.graphics.Bitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverShelfInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun books(count: Int) = (0 until count).map { index ->
        LocalBook(
            id = "b$index", uri = Uri.parse("file:///test-$index.txt"),
            title = "Book $index", format = "TXT", isFavorite = false,
            importedAt = index.toLong(), lastOpenedAt = null, readProgress = 0f,
            collectionId = null, offlineCopyUri = null,
        )
    }

    @Test
    fun nineVisibleCoversAndHorizontalPagesKeepTheirSlotWidthOnTheLastPage() {
        val catalog = books(20)
        compose.setContent {
            LibraryShelfTheme {
                CoverShelfPager(catalog, 450.dp, null, { _, _ -> }, {})
            }
        }
        repeat(9) { compose.onNodeWithTag("cover-book-b$it", useUnmergedTree = true).assertIsDisplayed() }
        savePreview("covers.png")
        val width = compose.onNodeWithTag("cover-book-b0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("cover-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        repeat(9) { compose.onNodeWithTag("cover-book-b${it + 9}", useUnmergedTree = true).assertIsDisplayed() }
        compose.onNodeWithTag("cover-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("cover-book-b18", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("cover-book-b19", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(width, compose.onNodeWithTag("cover-book-b18", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test
    fun holdingAndDraggingACoverCommitsItsNewOrderWithoutAMoveMenu() {
        val catalog = books(9)
        var committed: List<String>? = null
        compose.setContent {
            LibraryShelfTheme {
                CoverShelfPager(catalog, 450.dp, null, { _, _ -> }, { committed = it })
            }
        }
        val origin = compose.onNodeWithTag("cover-pager").fetchSemanticsNode().boundsInRoot.topLeft
        val first = compose.onNodeWithTag("cover-book-b0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - origin
        val second = compose.onNodeWithTag("cover-book-b1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center - origin
        compose.onNodeWithTag("cover-pager").performTouchInput {
            down(first)
            advanceEventTime(750)
            moveTo(first + Offset(2f, 0f))
            advanceEventTime(50)
            moveTo(second, delayMillis = 250)
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf("b1", "b0") + (2..8).map { "b$it" }, committed)
        }
    }

    @Test
    fun mixedShelfRendersBooksAndTransparentOrnamentsOnSupportedRows() {
        val catalog = books(8)
        val layout = BookshelfLayout.automatic(catalog.map { it.id })
            .addDecoration(ShelfDecorationType.PLANT, 0, 2)
            .addDecoration(ShelfDecorationType.LAVENDER, 1, 1)
            .addDecoration(ShelfDecorationType.GLOBE, 1, 3)
        val shelves = buildShelfDisplay(catalog, layout, true)
        compose.setContent {
            LibraryShelfTheme {
                Box(Modifier.fillMaxWidth().height(450.dp).background(MaterialTheme.colorScheme.background)) {
                    InteractiveMixedShelf(layout, shelves, catalog.associateBy { it.id }, 450.dp, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription(catalog.first().title).assertIsDisplayed()
        savePreview("mixed.png")
    }

    private fun savePreview(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "leepe-ui-preview")
        directory.mkdirs()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
