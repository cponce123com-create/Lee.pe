package pe.lee.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BookshelfLayoutTest {
    @Test
    fun replacingDecorationKeepsItsShelfPositionAndId() {
        val initial = BookshelfLayout().addDecoration(ShelfDecorationType.PLANT, shelf = 1, order = 3)
        val original = initial.decorations.single()

        val replaced = initial.replaceDecoration(original.id, ShelfDecorationType.MUG).decorations.single()

        assertEquals(original.id, replaced.id)
        assertEquals(original.shelf, replaced.shelf)
        assertEquals(original.order, replaced.order)
        assertEquals(ShelfDecorationType.MUG, replaced.type)
    }

    @Test
    fun customBooksAndDecorationsSurviveLocalLayoutSerialization() {
        val layout = BookshelfLayout()
            .reconcile(listOf("book-1", "book-2"))
            .moveBook("book-2", shelfDelta = 1)
            .addDecoration(ShelfDecorationType.DIVIDER, shelf = 1, order = 4)
        val encoded = BookshelfLayout.encode(layout)

        val restored = BookshelfLayout.decode(encoded)

        assertNotNull(restored)
        assertEquals(layout.books, restored?.books)
        assertEquals(layout.decorations, restored?.decorations)
        assertEquals(layout.hiddenBookIds, restored?.hiddenBookIds)
    }
}
