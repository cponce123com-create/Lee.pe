package pe.lee.app.ui.library

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BookshelfLayoutTest {
    @Test
    fun movingBeyondLastShelfPreservesDestinationAfterReload() {
        val initial = BookshelfLayout()
            .addToShelf("book")
            .addDecoration(ShelfDecorationType.PLANT, 4095, 0)
        assertEquals(null, initial.nextEmptyShelfIndex())

        val moved = initial.moveObject("book:book", initial.shelfCount(), 1)
        val restored = requireNotNull(BookshelfLayout.decode(BookshelfLayout.encode(moved)))

        assertEquals(4095, moved.books.single().shelf)
        assertEquals(moved.objectsByShelf(), restored.objectsByShelf())
    }

    @Test
    fun addingBooksAndDecorationsRespectsLastShelf() {
        val initial = BookshelfLayout()
            .addToShelf("a", 4096)
            .addDecoration(ShelfDecorationType.PLANT, 4096, 1)
        val reconciled = initial.reconcile(listOf("a", "b", "c", "d", "e", "f"))

        assertEquals(listOf(4095), reconciled.occupiedShelfIndices())
        assertEquals(7, reconciled.objectsOnShelf(4095).size)
    }

    @Test
    fun sparseDisplayReadsEachDecorationOnlyOnce() {
        val items = listOf(
            ShelfDecoration("high", ShelfDecorationType.PLANT, 4095, 0),
            ShelfDecoration("low", ShelfDecorationType.MUG, 0, 0),
        )
        var reads = 0
        val counted = object : AbstractList<ShelfDecoration>() {
            override val size: Int get() = items.size

            override fun get(index: Int): ShelfDecoration {
                reads++
                return items[index]
            }
        }
        val layout = BookshelfLayout(
            books = listOf(BookshelfBookPlacement("hidden", 12, 0, BookOrientation.UPRIGHT)),
            decorations = counted,
            hiddenBookIds = setOf("hidden"),
        )

        assertEquals(listOf(0, 4095), layout.occupiedShelfIndices())
        reads = 0
        val display = buildShelfDisplay(emptyList(), layout, customOrder = true)
        assertEquals(listOf(0, 4095), display.map { it.index })
        assertEquals(listOf("decor:low", "decor:high"), display.flatMap { it.objects }.map { it.stableKey })
        assertEquals(items.size, reads)
    }

    @Test
    fun mixedMovesPreserveUniqueObjectsContiguousOrdersAndRoundTrip() {
        val shelves = listOf(0, 1, 12, 4095)
        val books = (0..7).map { "book-$it" }
        val keys = books.map { "book:$it" } + shelves.map { "decor:decor-$it" }

        for (seed in 0..9) {
            val random = Random(seed)
            var layout = BookshelfLayout(
                decorations = shelves.map { ShelfDecoration("decor-$it", ShelfDecorationType.PLANT, it, 0) },
            ).reconcile(books)

            repeat(40) {
                val key = keys.random(random)
                val destination = shelves.random(random)
                layout = layout.moveObject(key, destination, random.nextInt(-1, keys.size + 1))
                val grouped = layout.objectsByShelf()
                val objects = grouped.values.flatten()

                assertEquals(keys.sorted(), objects.map { it.stableKey }.sorted())
                assertEquals(1, grouped.getValue(destination).count { it.stableKey == key })
                grouped.values.forEach { shelf ->
                    assertEquals(shelf.indices.toList(), shelf.map { it.order })
                }
                val restored = requireNotNull(BookshelfLayout.decode(BookshelfLayout.encode(layout)))
                assertEquals(grouped, restored.objectsByShelf())
            }
        }
    }

    @Test
    fun decodingExtremeShelfIndicesKeepsBooksAndDecorationsInRange() {
        val raw = """
            {
              "books": [{"id":"book","shelf":-42}],
              "decorations": [{"id":"plant","type":"PLANT","shelf":9223372036854775807}]
            }
        """.trimIndent()
        val layout = requireNotNull(BookshelfLayout.decode(raw))

        assertEquals(0, layout.books.single().shelf)
        assertEquals(4095, layout.decorations.single().shelf)
        assertEquals(4096, layout.shelfCount())
        assertEquals(listOf(0, 4095), layout.occupiedShelfIndices())
        assertEquals(layout, BookshelfLayout.decode(BookshelfLayout.encode(layout)))
        assertEquals(null, BookshelfLayout.decode("{"))
    }

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
        assertEquals(
            listOf("book-1", "book-2"),
            movedUp.objectsOnShelf(0).map { (it as ShelfObject.Book).placement.bookId },
        )
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
        assertEquals(
            objects.map { it.stableKey },
            rows.flatMap { it.groups }.flatMap { it.objects }.map { it.stableKey },
        )
        org.junit.Assert.assertTrue(rows.all { row -> row.groups.sumOf { it.units.toDouble() } <= 2.0 })
    }

    @Test
    fun stackedBooksRemainIndividualAndOrderedAcrossPackedRowsAndReload() {
        val ids = listOf("a", "b", "c", "d", "e")
        val initial = BookshelfLayout().reconcile(ids)
        val stacked = ids.fold(initial) { layout, id -> layout.toggleOrientation(id) }
        val moved = stacked.moveObject("book:e", 0, 0)
        val restored = requireNotNull(BookshelfLayout.decode(BookshelfLayout.encode(moved)))
        val rows = packMixedShelves(listOf(ShelfDisplay(0, restored.objectsOnShelf(0))), 3f)

        assertEquals(listOf(3, 2), rows.map { it.groups.single().objects.size })
        assertEquals(
            listOf("book:e", "book:a", "book:b", "book:c", "book:d"),
            rows.flatMap { it.groups }.flatMap { it.objects }.map { it.stableKey },
        )
        assertEquals(moved.objectsOnShelf(0), restored.objectsOnShelf(0))
    }

    @Test
    fun ornamentBeforeHorizontalBookSharesItsStackAfterReload() {
        val initial = BookshelfLayout().addToShelf("book").toggleOrientation("book")
            .addDecoration(ShelfDecorationType.PLANT, shelf = 0, order = 1)
        val ornament = "decor:${initial.decorations.single().id}"
        val moved = initial.moveObject(ornament, 0, 0)
        val restored = requireNotNull(BookshelfLayout.decode(BookshelfLayout.encode(moved)))

        val rows = packMixedShelves(listOf(ShelfDisplay(0, restored.objectsOnShelf(0))), 4f)
        assertEquals(
            listOf("book:book", ornament),
            rows.single().groups.single().objects.asReversed().map { it.stableKey },
        )
    }

    @Test
    fun ornamentCategoriesHaveExplicitMembershipIncludingLegacyTypes() {
        val expected = expectedOrnamentCategories()
        expected.forEach { (category, types) ->
            assertEquals(types, ShelfDecorationType.entries.filter { it.belongsTo(category) }.toSet())
        }
        assertTrue(ShelfDecorationType.VASE.belongsTo(ShelfDecorationCategory.ALL))
        assertFalse(ShelfDecorationType.VASE.belongsTo(ShelfDecorationCategory.NATURE))
    }

    @Test
    fun decoderSkipsMalformedRecordsAndFallsBackForInvalidBookOrientation() {
        val raw = """
            {
              "books": [
                {"id":"  ","shelf":2},
                {"id":"ok","shelf":2,"order":-4,"orientation":"SIDEWAYS"},
                "not-an-object"
              ],
              "decorations": [
                {"id":"unknown-type","type":"ALIEN","shelf":0},
                {"id":"","type":"PLANT","shelf":0},
                {"id":"kept","type":"PLANT","shelf":1,"order":-7},
                null
              ],
              "hidden": [{"id":"hidden"},{"id":"  "},false]
            }
        """.trimIndent()

        val layout = requireNotNull(BookshelfLayout.decode(raw))

        assertEquals(listOf(BookshelfBookPlacement("ok", 2, 0, BookOrientation.UPRIGHT)), layout.books)
        assertEquals(listOf(ShelfDecoration("kept", ShelfDecorationType.PLANT, 1, 0)), layout.decorations)
        assertEquals(setOf("hidden"), layout.hiddenBookIds)
    }

    private fun expectedOrnamentCategories() = mapOf(
        ShelfDecorationCategory.ALL to ShelfDecorationType.entries.toSet(),
        ShelfDecorationCategory.NATURE to setOf(
            ShelfDecorationType.PLANT, ShelfDecorationType.LAVENDER,
            ShelfDecorationType.BONSAI, ShelfDecorationType.IVY,
        ),
        ShelfDecorationCategory.MASCOTS to setOf(
            ShelfDecorationType.CAT, ShelfDecorationType.DOG, ShelfDecorationType.FOREST_SPIRIT,
            ShelfDecorationType.FOX, ShelfDecorationType.SOOT_CREATURE,
        ),
        ShelfDecorationCategory.READING to setOf(
            ShelfDecorationType.MUG, ShelfDecorationType.GLOBE, ShelfDecorationType.TYPEWRITER,
            ShelfDecorationType.HEADPHONES, ShelfDecorationType.GLOWING_BOOK, ShelfDecorationType.QUILL,
        ),
        ShelfDecorationCategory.FANTASY to setOf(
            ShelfDecorationType.ORB, ShelfDecorationType.FOREST_SPIRIT, ShelfDecorationType.FAIRY,
            ShelfDecorationType.DRAGON, ShelfDecorationType.MOON_STARS, ShelfDecorationType.SNOW_GLOBE,
            ShelfDecorationType.CASTLE, ShelfDecorationType.SOOT_CREATURE,
        ),
        ShelfDecorationCategory.ADVENTURE to setOf(
            ShelfDecorationType.ASTRONAUT, ShelfDecorationType.LIGHTHOUSE, ShelfDecorationType.ARCH,
            ShelfDecorationType.AIRSHIP, ShelfDecorationType.GLOBE,
        ),
        ShelfDecorationCategory.HOME to setOf(
            ShelfDecorationType.LAMP, ShelfDecorationType.CANDLE, ShelfDecorationType.HOURGLASS,
            ShelfDecorationType.LANTERN, ShelfDecorationType.CAT_BOX,
        ),
        ShelfDecorationCategory.COFFEE to setOf(
            ShelfDecorationType.MUG, ShelfDecorationType.COFFEE_SETUP, ShelfDecorationType.CANDLE,
        ),
        ShelfDecorationCategory.SEPARATORS to setOf(
            ShelfDecorationType.BOOKEND, ShelfDecorationType.DIVIDER, ShelfDecorationType.ARCH,
        ),
    )
}
