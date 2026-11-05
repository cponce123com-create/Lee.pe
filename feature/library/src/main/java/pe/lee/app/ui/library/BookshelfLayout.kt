package pe.lee.app.ui.library

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import pe.lee.feature.library.R

/** Stored with the library preferences; book IDs are stable Room IDs, never file paths. */
internal data class BookshelfBookPlacement(val bookId: String, val shelf: Int, val order: Int, val orientation: BookOrientation)

internal enum class BookOrientation { UPRIGHT, STACKED }

internal enum class ShelfDecorationType(val labelRes: Int, val drawableRes: Int) {
    BOOKEND(R.string.shelf_decor_owl, R.drawable.shelf_ornament_owl),
    PLANT(R.string.shelf_decor_plant, R.drawable.shelf_ornament_plant),
    MUG(R.string.shelf_decor_mug, R.drawable.shelf_ornament_mug),
    LAVENDER(R.string.shelf_decor_lavender, R.drawable.shelf_ornament_lavender),
    ORB(R.string.shelf_decor_orb, R.drawable.shelf_ornament_orb),
    BIRD(R.string.shelf_decor_bird, R.drawable.shelf_ornament_bird),
    LAMP(R.string.shelf_decor_lamp, R.drawable.shelf_ornament_lamp),
    DIVIDER(R.string.shelf_decor_divider, R.drawable.shelf_ornament_divider),
    CAT(R.string.shelf_decor_cat, R.drawable.shelf_ornament_cat),
    DOG(R.string.shelf_decor_dog, R.drawable.shelf_ornament_dog),
    GLOBE(R.string.shelf_decor_globe, R.drawable.shelf_ornament_globe),
    TYPEWRITER(R.string.shelf_decor_typewriter, R.drawable.shelf_ornament_typewriter),
    CANDLE(R.string.shelf_decor_candle, R.drawable.shelf_ornament_candle),
    HOURGLASS(R.string.shelf_decor_hourglass, R.drawable.shelf_ornament_hourglass),
    FOREST_SPIRIT(R.string.shelf_decor_forest_spirit, R.drawable.shelf_ornament_spirit),
    FOX(R.string.shelf_decor_fox, R.drawable.shelf_ornament_fox),
    HEADPHONES(R.string.shelf_decor_headphones, R.drawable.shelf_ornament_headphones),
    FAIRY(R.string.shelf_decor_fairy, R.drawable.shelf_ornament_fairy),
    DRAGON(R.string.shelf_decor_dragon, R.drawable.shelf_ornament_dragon),
    ASTRONAUT(R.string.shelf_decor_astronaut, R.drawable.shelf_ornament_astronaut),
    LIGHTHOUSE(R.string.shelf_decor_lighthouse, R.drawable.shelf_ornament_lighthouse),
    BONSAI(R.string.shelf_decor_bonsai, R.drawable.shelf_ornament_bonsai),
    ARCH(R.string.shelf_decor_arch, R.drawable.shelf_ornament_arch),
    AIRSHIP(R.string.shelf_decor_airship, R.drawable.shelf_ornament_airship),
    MOON_STARS(R.string.shelf_decor_moon_stars, R.drawable.shelf_ornament_moon),
    CAT_BOX(R.string.shelf_decor_cat_box, R.drawable.shelf_ornament_catbox),
    COFFEE_SETUP(R.string.shelf_decor_coffee_setup, R.drawable.shelf_ornament_coffee_setup),
    CAMERA(R.string.shelf_decor_camera, R.drawable.shelf_ornament_camera),
    SNOW_GLOBE(R.string.shelf_decor_snow_globe, R.drawable.shelf_ornament_snow_globe),
    LANTERN(R.string.shelf_decor_lantern, R.drawable.shelf_ornament_lantern),
    SOOT_CREATURE(R.string.shelf_decor_soot, R.drawable.shelf_ornament_soot),
    GLOWING_BOOK(R.string.shelf_decor_glowing_book, R.drawable.shelf_ornament_glowing_book),
    CASTLE(R.string.shelf_decor_castle, R.drawable.shelf_ornament_castle),
    QUILL(R.string.shelf_decor_quill, R.drawable.shelf_ornament_quill),
    IVY(R.string.shelf_decor_ivy, R.drawable.shelf_ornament_ivy),

    // Keep legacy values readable so existing saved shelf layouts retain their ornaments.
    VASE(R.string.shelf_decor_vase, R.drawable.shelf_ornament_vase),
    FIGURE(R.string.shelf_decor_figure, R.drawable.shelf_ornament_figure),
    SCULPTURE(R.string.shelf_decor_sculpture, R.drawable.shelf_ornament_figure),
}

internal val SHELF_DECORATION_GALLERY_TYPES = ShelfDecorationType.entries.filterNot {
    it == ShelfDecorationType.VASE || it == ShelfDecorationType.FIGURE || it == ShelfDecorationType.SCULPTURE
}

internal enum class ShelfDecorationCategory(val labelRes: Int) {
    ALL(R.string.shelf_category_all),
    NATURE(R.string.shelf_category_nature),
    MASCOTS(R.string.shelf_category_mascots),
    READING(R.string.shelf_category_reading),
    FANTASY(R.string.shelf_category_fantasy),
    ADVENTURE(R.string.shelf_category_adventure),
    HOME(R.string.shelf_category_home),
    COFFEE(R.string.shelf_category_coffee),
    SEPARATORS(R.string.shelf_category_separators),
}

internal fun ShelfDecorationType.belongsTo(category: ShelfDecorationCategory): Boolean = when (category) {
    ShelfDecorationCategory.ALL -> true
    ShelfDecorationCategory.NATURE ->
        this in
            setOf(ShelfDecorationType.PLANT, ShelfDecorationType.LAVENDER, ShelfDecorationType.BONSAI, ShelfDecorationType.IVY)
    ShelfDecorationCategory.MASCOTS ->
        this in
            setOf(
                ShelfDecorationType.CAT,
                ShelfDecorationType.DOG,
                ShelfDecorationType.FOREST_SPIRIT,
                ShelfDecorationType.FOX,
                ShelfDecorationType.SOOT_CREATURE,
            )
    ShelfDecorationCategory.READING ->
        this in
            setOf(
                ShelfDecorationType.MUG,
                ShelfDecorationType.GLOBE,
                ShelfDecorationType.TYPEWRITER,
                ShelfDecorationType.HEADPHONES,
                ShelfDecorationType.GLOWING_BOOK,
                ShelfDecorationType.QUILL,
            )
    ShelfDecorationCategory.FANTASY ->
        this in
            setOf(
                ShelfDecorationType.ORB,
                ShelfDecorationType.FOREST_SPIRIT,
                ShelfDecorationType.FAIRY,
                ShelfDecorationType.DRAGON,
                ShelfDecorationType.MOON_STARS,
                ShelfDecorationType.SNOW_GLOBE,
                ShelfDecorationType.CASTLE,
                ShelfDecorationType.SOOT_CREATURE,
            )
    ShelfDecorationCategory.ADVENTURE ->
        this in
            setOf(
                ShelfDecorationType.ASTRONAUT,
                ShelfDecorationType.LIGHTHOUSE,
                ShelfDecorationType.ARCH,
                ShelfDecorationType.AIRSHIP,
                ShelfDecorationType.GLOBE,
            )
    ShelfDecorationCategory.HOME ->
        this in
            setOf(
                ShelfDecorationType.LAMP,
                ShelfDecorationType.CANDLE,
                ShelfDecorationType.HOURGLASS,
                ShelfDecorationType.LANTERN,
                ShelfDecorationType.CAT_BOX,
            )
    ShelfDecorationCategory.COFFEE -> this in setOf(ShelfDecorationType.MUG, ShelfDecorationType.COFFEE_SETUP, ShelfDecorationType.CANDLE)
    ShelfDecorationCategory.SEPARATORS -> this in setOf(ShelfDecorationType.BOOKEND, ShelfDecorationType.DIVIDER, ShelfDecorationType.ARCH)
}

internal data class ShelfDecoration(val id: String, val type: ShelfDecorationType, val shelf: Int, val order: Int)

internal data class BookshelfLayout(
    val books: List<BookshelfBookPlacement> = emptyList(),
    val decorations: List<ShelfDecoration> = emptyList(),
    val hiddenBookIds: Set<String> = emptySet(),
) {
    fun reconcile(bookIds: List<String>): BookshelfLayout {
        val currentIds = bookIds.toSet()
        val keptBooks = books.filter { it.bookId in currentIds }
        val keptIds = keptBooks.mapTo(mutableSetOf()) { it.bookId }
        val retainedHidden = hiddenBookIds.intersect(currentIds)
        val additions = mutableListOf<BookshelfBookPlacement>()
        bookIds.filter { it !in keptIds && it !in retainedHidden }.forEach { id ->
            val lastShelf = (keptBooks + additions).maxOfOrNull { it.shelf } ?: 0
            val lastOrder = (keptBooks + additions).filter { it.shelf == lastShelf }.maxOfOrNull { it.order } ?: -1
            val shelf = if (lastOrder + 1 >= INITIAL_BOOKS_PER_SHELF) lastShelf + 1 else lastShelf
            val order = if (shelf == lastShelf) lastOrder + 1 else 0
            additions += BookshelfBookPlacement(id, shelf, order, BookOrientation.UPRIGHT)
        }
        return copy(
            books = keptBooks.filterNot { it.bookId in retainedHidden }.distinctBy { it.bookId } + additions,
            decorations = decorations.filter { it.shelf >= 0 }.distinctBy { it.id },
            hiddenBookIds = retainedHidden,
        ).normalizedObjectOrders()
    }

    fun moveBook(bookId: String, shelfDelta: Int = 0, orderDelta: Int = 0): BookshelfLayout {
        val current = books.firstOrNull { it.bookId == bookId } ?: return this
        val sourceIndex = objectsOnShelf(current.shelf).indexOfFirst { it.stableKey == "book:$bookId" }
        return moveObject("book:$bookId", current.shelf + shelfDelta, sourceIndex + orderDelta)
    }

    /** Reorders cover slots while preserving ornaments and books outside the active filter. */
    fun reorderBooks(ids: List<String>): BookshelfLayout {
        val byId = books.associateBy { it.bookId }
        val ordered = ids.distinct().mapNotNull(byId::get).iterator()
        val selected = ids.toSet()
        val slots = books.sortedWith(compareBy({ it.shelf }, { it.order }))
        return copy(
            books = slots.map { slot ->
                if (slot.bookId in selected && ordered.hasNext()) {
                    ordered.next().copy(shelf = slot.shelf, order = slot.order)
                } else {
                    slot
                }
            },
        ).normalizedObjectOrders()
    }

    fun toggleOrientation(bookId: String): BookshelfLayout = copy(
        books = books.map { placement ->
            if (placement.bookId != bookId) {
                placement
            } else {
                placement.copy(
                    orientation = if (placement.orientation ==
                        BookOrientation.UPRIGHT
                    ) {
                        BookOrientation.STACKED
                    } else {
                        BookOrientation.UPRIGHT
                    },
                )
            }
        },
    )

    fun removeFromShelf(bookId: String): BookshelfLayout = copy(
        books = books.filterNot { it.bookId == bookId },
        hiddenBookIds = hiddenBookIds + bookId,
    ).normalizedObjectOrders()

    fun addToShelf(bookId: String, shelf: Int = 0): BookshelfLayout {
        if (books.any { it.bookId == bookId }) return copy(hiddenBookIds = hiddenBookIds - bookId)
        val order = (objectsOnShelf(shelf).maxOfOrNull { it.order } ?: -1) + 1
        return copy(
            books = books + BookshelfBookPlacement(bookId, shelf, order, BookOrientation.UPRIGHT),
            hiddenBookIds = hiddenBookIds - bookId,
        ).normalizedObjectOrders()
    }

    fun addDecoration(type: ShelfDecorationType, shelf: Int, order: Int): BookshelfLayout {
        if (decorations.size >= MAX_SHELF_DECORATIONS) return this
        val decoration = ShelfDecoration(UUID.randomUUID().toString(), type, shelf.coerceAtLeast(0), order.coerceAtLeast(0))
        return copy(decorations = decorations + decoration).normalizedObjectOrders()
    }

    fun moveDecoration(id: String, shelfDelta: Int = 0, orderDelta: Int = 0): BookshelfLayout {
        val current = decorations.firstOrNull { it.id == id } ?: return this
        val sourceIndex = objectsOnShelf(current.shelf).indexOfFirst { it.stableKey == "decor:$id" }
        return moveObject("decor:$id", current.shelf + shelfDelta, sourceIndex + orderDelta)
    }

    /** Both object types share one insertion order, including during a drag preview. */
    fun moveObject(key: String, targetShelf: Int, targetIndex: Int): BookshelfLayout {
        val sourceShelf = occupiedShelfIndices().firstOrNull { shelf ->
            objectsOnShelf(shelf).any { it.stableKey == key }
        } ?: return this
        val destination = targetShelf.coerceAtLeast(0)
        val source = objectsOnShelf(sourceShelf).toMutableList()
        val moving = source.firstOrNull { it.stableKey == key } ?: return this
        source.remove(moving)
        val target = if (destination == sourceShelf) source else objectsOnShelf(destination).toMutableList()
        target.add(targetIndex.coerceIn(0, target.size), moving)
        return withShelfObjects(
            if (destination == sourceShelf) mapOf(sourceShelf to target) else mapOf(sourceShelf to source, destination to target),
        )
    }

    fun removeDecoration(id: String): BookshelfLayout = copy(decorations = decorations.filterNot { it.id == id })

    fun replaceDecoration(id: String, type: ShelfDecorationType): BookshelfLayout = copy(
        decorations = decorations.map { decoration ->
            if (decoration.id == id) decoration.copy(type = type) else decoration
        },
    )

    fun occupiedShelfIndices(): List<Int> = buildSet {
        books.filterNot { it.bookId in hiddenBookIds }.forEach { add(it.shelf) }
        decorations.forEach { add(it.shelf) }
    }.sorted()

    fun objectsOnShelf(shelf: Int): List<ShelfObject> = buildList {
        books.filter { it.shelf == shelf && it.bookId !in hiddenBookIds }.forEach {
            add(ShelfObject.Book(it))
        }
        decorations.filter { it.shelf == shelf }.forEach { add(ShelfObject.Decor(it)) }
    }.sortedWith(compareBy({ it.order }, { if (it is ShelfObject.Book) 0 else 1 }))

    fun shelfCount(): Int = maxOf(
        books.filterNot { it.bookId in hiddenBookIds }.maxOfOrNull { it.shelf + 1 } ?: 0,
        decorations.maxOfOrNull { it.shelf + 1 } ?: 0,
        1,
    )

    private fun normalizedObjectOrders(): BookshelfLayout =
        withShelfObjects((books.map { it.shelf } + decorations.map { it.shelf }).distinct().associateWith(::objectsOnShelf))

    private fun withShelfObjects(shelves: Map<Int, List<ShelfObject>>): BookshelfLayout = copy(
        books = books.filterNot { it.shelf in shelves } + shelves.flatMap { (shelf, objects) ->
            objects.mapIndexedNotNull { index, item ->
                (item as? ShelfObject.Book)?.placement?.copy(shelf = shelf, order = index)
            }
        },
        decorations = decorations.filterNot { it.shelf in shelves } + shelves.flatMap { (shelf, objects) ->
            objects.mapIndexedNotNull { index, item ->
                (item as? ShelfObject.Decor)?.decoration?.copy(shelf = shelf, order = index)
            }
        },
    )

    companion object {
        private const val INITIAL_BOOKS_PER_SHELF = 5
        private const val MAX_PERSISTED_SHELF_INDEX = 4095

        fun automatic(bookIds: List<String>): BookshelfLayout = BookshelfLayout().reconcile(bookIds).let { layout ->
            layout.copy(
                books = layout.books.mapIndexed { index, placement ->
                    if (index % 4 == 2) placement.copy(orientation = BookOrientation.STACKED) else placement
                },
            )
        }

        fun decode(raw: String?): BookshelfLayout? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                val placements = root.optJSONArray("books").toObjectList().mapNotNull { obj ->
                    val id = obj.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    BookshelfBookPlacement(
                        bookId = id,
                        shelf = obj.optLong("shelf", 0).coerceIn(0, MAX_PERSISTED_SHELF_INDEX.toLong()).toInt(),
                        order = obj.optInt("order", 0).coerceAtLeast(0),
                        orientation = runCatching {
                            BookOrientation.valueOf(obj.optString("orientation"))
                        }.getOrDefault(BookOrientation.UPRIGHT),
                    )
                }
                val decorations = root.optJSONArray("decorations").toObjectList().mapNotNull { obj ->
                    val id = obj.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val type = runCatching { ShelfDecorationType.valueOf(obj.optString("type")) }.getOrNull() ?: return@mapNotNull null
                    ShelfDecoration(id, type, obj.optLong("shelf", 0).coerceIn(0, MAX_PERSISTED_SHELF_INDEX.toLong()).toInt(), obj.optInt("order", 0).coerceAtLeast(0))
                }
                val hidden = root.optJSONArray("hidden").toObjectList().mapNotNull { it.optString("id").takeIf(String::isNotBlank) }.toSet()
                BookshelfLayout(placements, decorations, hidden)
            }.getOrNull()
        }

        fun encode(layout: BookshelfLayout): String = JSONObject().apply {
            put("version", 1)
            put(
                "books",
                JSONArray().apply {
                    layout.books.sortedWith(compareBy({ it.shelf }, { it.order })).forEach { item ->
                        put(
                            JSONObject().put(
                                "id",
                                item.bookId,
                            ).put("shelf", item.shelf).put("order", item.order).put("orientation", item.orientation.name),
                        )
                    }
                },
            )
            put(
                "decorations",
                JSONArray().apply {
                    layout.decorations.forEach { item ->
                        put(JSONObject().put("id", item.id).put("type", item.type.name).put("shelf", item.shelf).put("order", item.order))
                    }
                },
            )
            put("hidden", JSONArray().apply { layout.hiddenBookIds.forEach { put(JSONObject().put("id", it)) } })
        }.toString()

        private fun JSONArray?.toObjectList(): List<JSONObject> = buildList {
            if (this@toObjectList == null) return@buildList
            for (i in 0 until length()) optJSONObject(i)?.let(::add)
        }
    }
}

internal sealed interface ShelfObject {
    val order: Int
    data class Book(val placement: BookshelfBookPlacement) : ShelfObject {
        override val order get() = placement.order
    }
    data class Decor(val decoration: ShelfDecoration) : ShelfObject {
        override val order get() = decoration.order
    }
}

internal const val BOOKSHELF_LAYOUT_KEY = "bookshelf_layout_v1"
internal const val BOOKSHELF_VIEW_MODE_KEY = "bookshelf_view_mode"
internal const val BOOKSHELF_SORT_MODE_KEY = "bookshelf_sort_mode"

/** Stable gesture identity, independent of a URI, page or shelf position. */
internal val ShelfObject.stableKey: String
    get() = when (this) {
        is ShelfObject.Book -> "book:${placement.bookId}"
        is ShelfObject.Decor -> "decor:${decoration.id}"
    }

internal const val MAX_SHELF_DECORATIONS = 10
