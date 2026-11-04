package pe.lee.app.ui.library

import org.json.JSONArray
import org.json.JSONObject
import pe.lee.feature.library.R
import java.util.UUID

/** Stored with the library preferences; book IDs are stable Room IDs, never file paths. */
internal data class BookshelfBookPlacement(
    val bookId: String,
    val shelf: Int,
    val order: Int,
    val orientation: BookOrientation
)

internal enum class BookOrientation { UPRIGHT, STACKED }

internal enum class ShelfDecorationType(val labelRes: Int, val drawableRes: Int) {
    BOOKEND(R.string.shelf_decor_bookend, R.drawable.shelf_ornament_bookend),
    PLANT(R.string.shelf_decor_plant, R.drawable.shelf_ornament_plant),
    MUG(R.string.shelf_decor_mug, R.drawable.shelf_ornament_mug),
    VASE(R.string.shelf_decor_vase, R.drawable.shelf_ornament_vase),
    DIVIDER(R.string.shelf_decor_divider, R.drawable.shelf_ornament_divider),
    LAMP(R.string.shelf_decor_lamp, R.drawable.shelf_ornament_lamp),
    ORB(R.string.shelf_decor_orb, R.drawable.shelf_ornament_orb),
    FIGURE(R.string.shelf_decor_figure, R.drawable.shelf_ornament_figure),
    SCULPTURE(R.string.shelf_decor_sculpture, R.drawable.shelf_ornament_figure)
}

internal enum class ShelfDecorationCategory(val labelRes: Int) {
    ALL(R.string.shelf_category_all),
    NATURE(R.string.shelf_category_nature),
    READING(R.string.shelf_category_reading),
    FANTASY(R.string.shelf_category_fantasy),
    MINIMAL(R.string.shelf_category_minimal),
    SEPARATORS(R.string.shelf_category_separators)
}

internal fun ShelfDecorationType.belongsTo(category: ShelfDecorationCategory): Boolean = when (category) {
    ShelfDecorationCategory.ALL -> true
    ShelfDecorationCategory.NATURE -> this == PLANT || this == VASE
    ShelfDecorationCategory.READING -> this == MUG || this == LAMP
    ShelfDecorationCategory.FANTASY -> this == BOOKEND || this == ORB || this == FIGURE || this == SCULPTURE
    ShelfDecorationCategory.MINIMAL -> this == SCULPTURE || this == ORB
    ShelfDecorationCategory.SEPARATORS -> this == BOOKEND || this == DIVIDER
}

internal data class ShelfDecoration(
    val id: String,
    val type: ShelfDecorationType,
    val shelf: Int,
    val order: Int
)

internal data class BookshelfLayout(
    val books: List<BookshelfBookPlacement> = emptyList(),
    val decorations: List<ShelfDecoration> = emptyList(),
    val hiddenBookIds: Set<String> = emptySet()
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
            books = (keptBooks + additions).normalizedOrders(),
            decorations = decorations.filter { it.shelf >= 0 },
            hiddenBookIds = retainedHidden
        )
    }

    fun moveBook(bookId: String, shelfDelta: Int = 0, orderDelta: Int = 0): BookshelfLayout {
        val current = books.firstOrNull { it.bookId == bookId } ?: return this
        val targetShelf = (current.shelf + shelfDelta).coerceAtLeast(0)
        val targetOrder = (current.order + orderDelta).coerceAtLeast(0)
        val remaining = books.filterNot { it.bookId == bookId }.toMutableList()
        val sameShelf = remaining.filter { it.shelf == targetShelf }.sortedBy { it.order }.toMutableList()
        sameShelf.add(targetOrder.coerceAtMost(sameShelf.size), current.copy(shelf = targetShelf, order = targetOrder))
        val renumberedShelf = sameShelf.mapIndexed { index, item -> item.copy(shelf = targetShelf, order = index) }
        val all = (remaining.filterNot { it.shelf == targetShelf } + renumberedShelf).normalizedOrders()
        return copy(books = all, hiddenBookIds = hiddenBookIds - bookId)
    }

    fun toggleOrientation(bookId: String): BookshelfLayout = copy(
        books = books.map { placement ->
            if (placement.bookId != bookId) placement else placement.copy(
                orientation = if (placement.orientation == BookOrientation.UPRIGHT) BookOrientation.STACKED else BookOrientation.UPRIGHT
            )
        }
    )

    fun removeFromShelf(bookId: String): BookshelfLayout = copy(
        books = books.filterNot { it.bookId == bookId }.normalizedOrders(),
        hiddenBookIds = hiddenBookIds + bookId
    )

    fun addToShelf(bookId: String, shelf: Int = 0): BookshelfLayout {
        if (books.any { it.bookId == bookId }) return copy(hiddenBookIds = hiddenBookIds - bookId)
        val order = (books.filter { it.shelf == shelf }.maxOfOrNull { it.order } ?: -1) + 1
        return copy(
            books = (books + BookshelfBookPlacement(bookId, shelf, order, BookOrientation.UPRIGHT)).normalizedOrders(),
            hiddenBookIds = hiddenBookIds - bookId
        )
    }

    fun addDecoration(type: ShelfDecorationType, shelf: Int, order: Int): BookshelfLayout {
        val decoration = ShelfDecoration(UUID.randomUUID().toString(), type, shelf.coerceAtLeast(0), order.coerceAtLeast(0))
        return copy(decorations = decorations + decoration)
    }

    fun moveDecoration(id: String, shelfDelta: Int = 0, orderDelta: Int = 0): BookshelfLayout = copy(
        decorations = decorations.map { item ->
            if (item.id != id) item else item.copy(shelf = (item.shelf + shelfDelta).coerceAtLeast(0), order = (item.order + orderDelta).coerceAtLeast(0))
        }
    )

    fun removeDecoration(id: String): BookshelfLayout = copy(decorations = decorations.filterNot { it.id == id })

    fun replaceDecoration(id: String, type: ShelfDecorationType): BookshelfLayout = copy(
        decorations = decorations.map { decoration ->
            if (decoration.id == id) decoration.copy(type = type) else decoration
        }
    )

    fun objectsOnShelf(shelf: Int): List<ShelfObject> = buildList {
        books.filter { it.shelf == shelf && it.bookId !in hiddenBookIds }.forEach {
            add(ShelfObject.Book(it))
        }
        decorations.filter { it.shelf == shelf }.forEach { add(ShelfObject.Decor(it)) }
    }.sortedWith(compareBy({ it.order }, { if (it is ShelfObject.Book) 0 else 1 }))

    fun shelfCount(): Int = maxOf(
        books.filterNot { it.bookId in hiddenBookIds }.maxOfOrNull { it.shelf + 1 } ?: 0,
        decorations.maxOfOrNull { it.shelf + 1 } ?: 0,
        1
    )

    private fun List<BookshelfBookPlacement>.normalizedOrders(): List<BookshelfBookPlacement> =
        groupBy { it.shelf }.toSortedMap().values.flatMap { group ->
            group.sortedBy { it.order }.mapIndexed { index, item -> item.copy(order = index) }
        }

    companion object {
        private const val INITIAL_BOOKS_PER_SHELF = 5

        fun automatic(bookIds: List<String>): BookshelfLayout = BookshelfLayout().reconcile(bookIds).let { layout ->
            layout.copy(books = layout.books.mapIndexed { index, placement ->
                if (index % 4 == 2) placement.copy(orientation = BookOrientation.STACKED) else placement
            })
        }

        fun decode(raw: String?): BookshelfLayout? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                val placements = root.optJSONArray("books").toObjectList().mapNotNull { obj ->
                    val id = obj.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    BookshelfBookPlacement(
                        bookId = id,
                        shelf = obj.optInt("shelf", 0).coerceAtLeast(0),
                        order = obj.optInt("order", 0).coerceAtLeast(0),
                        orientation = runCatching { BookOrientation.valueOf(obj.optString("orientation")) }.getOrDefault(BookOrientation.UPRIGHT)
                    )
                }
                val decorations = root.optJSONArray("decorations").toObjectList().mapNotNull { obj ->
                    val id = obj.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val type = runCatching { ShelfDecorationType.valueOf(obj.optString("type")) }.getOrNull() ?: return@mapNotNull null
                    ShelfDecoration(id, type, obj.optInt("shelf", 0).coerceAtLeast(0), obj.optInt("order", 0).coerceAtLeast(0))
                }
                val hidden = root.optJSONArray("hidden").toObjectList().mapNotNull { it.optString("id").takeIf(String::isNotBlank) }.toSet()
                BookshelfLayout(placements, decorations, hidden)
            }.getOrNull()
        }

        fun encode(layout: BookshelfLayout): String = JSONObject().apply {
            put("version", 1)
            put("books", JSONArray().apply {
                layout.books.sortedWith(compareBy({ it.shelf }, { it.order })).forEach { item ->
                    put(JSONObject().put("id", item.bookId).put("shelf", item.shelf).put("order", item.order).put("orientation", item.orientation.name))
                }
            })
            put("decorations", JSONArray().apply {
                layout.decorations.forEach { item ->
                    put(JSONObject().put("id", item.id).put("type", item.type.name).put("shelf", item.shelf).put("order", item.order))
                }
            })
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
    data class Book(val placement: BookshelfBookPlacement) : ShelfObject { override val order get() = placement.order }
    data class Decor(val decoration: ShelfDecoration) : ShelfObject { override val order get() = decoration.order }
}

internal const val BOOKSHELF_LAYOUT_KEY = "bookshelf_layout_v1"
internal const val BOOKSHELF_VIEW_MODE_KEY = "bookshelf_view_mode"
internal const val BOOKSHELF_SORT_MODE_KEY = "bookshelf_sort_mode"
