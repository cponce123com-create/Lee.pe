package pe.lee.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BookshelfLayoutTest {
    @Test
    fun ornamentGalleryContainsAllThirtyFiveReferenceItems() {
        assertEquals(35, SHELF_DECORATION_GALLERY_TYPES.size)
        assertEquals(9, ShelfDecorationCategory.entries.size)
        assertEquals(35, SHELF_DECORATION_GALLERY_TYPES.map { it.drawableRes }.distinct().size)
    }

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
    fun movingDecorationReordersItWithBooksAndCanMoveItToAnotherShelf() {
        val initial = BookshelfLayout()
            .reconcile(listOf("book-1", "book-2"))
            .addDecoration(ShelfDecorationType.DIVIDER, shelf = 0, order = 1)
        val decoration = initial.decorations.single()

        val movedLeft = initial.moveDecoration(decoration.id, orderDelta = -1)
        assertEquals(
            listOf("book-1", decoration.id, "book-2"),
            movedLeft.objectsOnShelf(0).map { item ->
                when (item) {
                    is ShelfObject.Book -> item.placement.bookId
                    is ShelfObject.Decor -> item.decoration.id
                }
            },
        )

        val movedUp = movedLeft.moveDecoration(decoration.id, shelfDelta = 1)
        assertEquals(1, movedUp.decorations.single().shelf)
        assertEquals(listOf(decoration.id), movedUp.objectsOnShelf(1).map { (it as ShelfObject.Decor).decoration.id })
        assertEquals(listOf("book-1", "book-2"), movedUp.objectsOnShelf(0).map { (it as ShelfObject.Book).placement.bookId })
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

    @Test
    fun savingMixedOrderDoesNotMoveTheDecorationToTheEnd() {
        val initial = BookshelfLayout().reconcile(listOf("a", "b"))
            .addDecoration(ShelfDecorationType.PLANT, 0, 2)
        val id = initial.decorations.single().id
        val edited = initial.moveDecoration(id, orderDelta = -1)
        val restored = BookshelfLayout.decode(BookshelfLayout.encode(edited))!!.reconcile(listOf("a", "b"))
        assertEquals(listOf("book:a", "decor:$id", "book:b"), restored.objectsOnShelf(0).map { it.stableKey })
    }

    @Test
    fun bookAndDecorationUseTheSameMovementSlots() {
        val initial = BookshelfLayout().reconcile(listOf("a", "b"))
            .addDecoration(ShelfDecorationType.PLANT, 0, 2)
        val id = initial.decorations.single().id
        val mixed = initial.moveDecoration(id, orderDelta = -1)
        val moved = mixed.moveBook("a", orderDelta = 1).reconcile(listOf("a", "b"))
        assertEquals(listOf("decor:$id", "book:a", "book:b"), moved.objectsOnShelf(0).map { it.stableKey })
    }

    @Test
    fun tenOrnamentsCanBeAddedButEleventhDoesNotChangeTheLayout() {
        var layout = BookshelfLayout()
        repeat(10) { layout = layout.addDecoration(ShelfDecorationType.PLANT, 0, it) }
        assertEquals(10, layout.decorations.size)
        assertEquals(layout, layout.addDecoration(ShelfDecorationType.MUG, 0, 10))
        val replaced = layout.replaceDecoration(layout.decorations.first().id, ShelfDecorationType.MUG)
        assertEquals(10, replaced.decorations.size)
        assertEquals(ShelfDecorationType.MUG, replaced.decorations.first().type)
    }

    @Test
    fun filteredCoverReorderPreservesOtherBooksAndOrnaments() {
        val initial = BookshelfLayout().reconcile(listOf("a", "b", "c"))
            .addDecoration(ShelfDecorationType.PLANT, 0, 3)
        val moved = initial.reorderBooks(listOf("c", "a"))
        assertEquals(listOf("c", "b", "a"), moved.books.sortedBy { it.order }.map { it.bookId })
        assertEquals(initial.decorations, moved.decorations)
    }

    @Test
    fun draggingAcrossShelvesRetainsEveryObjectAndSurvivesReconciliation() {
        val initial = BookshelfLayout().reconcile(listOf("a", "b", "c", "d", "e", "f"))
            .addDecoration(ShelfDecorationType.LAMP, 0, 5)
        val key = "decor:${initial.decorations.single().id}"
        val moved = initial.moveObject(key, 1, 0).reconcile(listOf("a", "b", "c", "d", "e", "f"))
        assertEquals(key, moved.objectsOnShelf(1).first().stableKey)
        assertEquals(6, moved.books.map { it.bookId }.distinct().size)
        assertEquals(1, moved.decorations.size)
    }

    @Test
    fun packedRowsHaveAPlankForEveryOverflowLineAndKeepAllObjects() {
        val layout = BookshelfLayout().reconcile(listOf("a", "b", "c", "d", "e"))
        val objects = layout.objectsOnShelf(0)
        val rows = packMixedShelves(listOf(ShelfDisplay(0, objects)), 2f)
        assertEquals(3, rows.size)
        assertEquals(objects.map { it.stableKey }, rows.flatMap { it.groups }.flatMap { it.objects }.map { it.stableKey })
        org.junit.Assert.assertTrue(rows.all { row -> row.groups.sumOf { it.units.toDouble() } <= 2.0 })
    }
}
