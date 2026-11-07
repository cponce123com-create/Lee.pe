package pe.lee.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class MixedShelfGroup(val objects: List<ShelfObject>, val units: Float)
internal data class MixedShelfLine(val logicalShelf: Int, val groups: List<MixedShelfGroup>)

private val mixedBookPalette = listOf(
    Color(0xFF234F3D),
    Color(0xFF214C70),
    Color(0xFF702F37),
    Color(0xFF164B4C),
    Color(0xFF75432B),
)

private const val MIXED_ROWS_PER_PAGE = 2
private const val MIXED_ROW_WIDTH = .85f
private val mixedRowTops = listOf(.138f, .504f).map(::mixedCabinetY)
private val mixedRowBottoms = listOf(.463f, .824f).map(::mixedCabinetY)

private fun ShelfObject.isHorizontalBook(): Boolean = this is ShelfObject.Book && placement.orientation == BookOrientation.STACKED

private const val HORIZONTAL_BOOK_UNITS = 2.85f

/** Capacity is measured before layout so every visual line gets its own supporting plank. */
internal fun packMixedShelves(shelves: List<ShelfDisplay>, capacity: Float, gapUnits: Float = 0f): List<MixedShelfLine> = buildList {
    shelves.forEach { shelf ->
        val groups = mutableListOf<MixedShelfGroup>()
        shelf.objects.forEach { item ->
            val previous = groups.lastOrNull()
            val stacked = item.isHorizontalBook()
            val previousStackSize = previous?.objects?.count { it.isHorizontalBook() } ?: 0
            val canExtend = previous != null &&
                stacked &&
                previousStackSize < 3 &&
                (previousStackSize > 0 || previous.objects.singleOrNull() is ShelfObject.Decor)
            if (canExtend && previous != null) {
                groups[groups.lastIndex] = MixedShelfGroup(previous.objects + item, HORIZONTAL_BOOK_UNITS)
            } else {
                val units = when {
                    stacked -> HORIZONTAL_BOOK_UNITS
                    item is ShelfObject.Decor -> 1.65f
                    else -> .98f
                }
                groups += MixedShelfGroup(listOf(item), units)
            }
        }
        var line = mutableListOf<MixedShelfGroup>()
        var used = 0f
        groups.forEach { group ->
            if (line.isNotEmpty() && used + gapUnits + group.units > capacity) {
                add(MixedShelfLine(shelf.index, line.toList()))
                line = mutableListOf()
                used = 0f
            }
            if (line.isNotEmpty()) used += gapUnits
            line += group
            used += group.units
        }
        if (line.isNotEmpty()) add(MixedShelfLine(shelf.index, line.toList()))
    }
}

@Composable
internal fun InteractiveMixedShelf(
    layout: BookshelfLayout,
    shelves: List<ShelfDisplay>,
    booksById: Map<String, LocalBook>,
    height: Dp,
    onOpen: (LocalBook) -> Unit,
    onCommit: (BookshelfLayout) -> Unit,
    onDecorationClick: (String) -> Unit,
) {
    val drag = remember { ShelfDragState() }
    val visibleIds = shelves.flatMap { it.objects }.filterIsInstance<ShelfObject.Book>().map { it.placement.bookId }.toSet()
    val displayedBooks = shelves.flatMap { it.objects }.filterIsInstance<ShelfObject.Book>().map { it.placement }
    val initial = layout.copy(books = layout.books.filterNot { it.bookId in visibleIds } + displayedBooks)
    var draft by remember { mutableStateOf(initial) }
    var beforeDrag by remember { mutableStateOf(initial) }
    var edgeDirection by remember { mutableStateOf(0) }
    var viewportWidth by remember { mutableStateOf(0f) }
    val edge = with(LocalDensity.current) { 28.dp.toPx() }
    LaunchedEffect(layout, shelves) { if (drag.activeKey == null) draft = initial }
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val availableWidth = minOf(maxWidth.value, maxHeight.value * MIXED_CABINET_ASPECT) * MIXED_ROW_WIDTH
        val capacity = availableWidth / 45f
        val shown = draft.objectsByShelf().mapNotNull { (shelf, items) ->
            val objects = items.filter { it !is ShelfObject.Book || it.placement.bookId in visibleIds }
            objects.takeIf { it.isNotEmpty() }?.let { ShelfDisplay(shelf, it) }
        }
        val lines = packMixedShelves(shown, capacity, gapUnits = 3f / 45f).let { packed ->
            val emptyShelf = beforeDrag.nextEmptyShelfIndex()
            if (drag.activeKey != null && emptyShelf != null && shown.none { it.index == emptyShelf }) {
                packed + MixedShelfLine(emptyShelf, emptyList())
            } else {
                packed
            }
        }
        val pages = ((lines.size + MIXED_ROWS_PER_PAGE - 1) / MIXED_ROWS_PER_PAGE).coerceAtLeast(1)
        val pager = rememberPagerState(pageCount = { pages })
        val scope = rememberCoroutineScope()
        LaunchedEffect(drag.activeKey, edgeDirection, pages) {
            while (drag.activeKey != null && edgeDirection != 0) {
                delay(650)
                val target = (pager.currentPage + edgeDirection).coerceIn(0, pages - 1)
                if (target != pager.currentPage) pager.animateScrollToPage(target)
            }
        }
        ShelfDragSurface(
            state = drag,
            modifier = Modifier.fillMaxSize().onGloballyPositioned { viewportWidth = it.size.width.toFloat() },
            onStart = { beforeDrag = draft },
            onHover = { moving, target ->
                val grouped = draft.objectsByShelf()
                val destination = if (target.startsWith("zone:")) {
                    target.substringAfter("zone:").substringBefore(':').toIntOrNull()
                } else {
                    grouped.entries.firstOrNull { (_, objects) ->
                        objects.any { it.stableKey == target }
                    }?.key
                }
                if (destination != null) {
                    val objects = grouped[destination].orEmpty()
                    val stack = lines.asSequence().flatMap { it.groups.asSequence() }.firstOrNull { group ->
                        group.objects.any { it.stableKey == target && it.isHorizontalBook() }
                    }
                    val movingDecor = moving.startsWith("decor:")
                    val anchor = if (movingDecor) stack?.objects?.firstOrNull { it.isHorizontalBook() }?.stableKey else null
                    val index = if (target.startsWith("zone:")) {
                        objects.size
                    } else {
                        val targetIndex = objects.indexOfFirst { it.stableKey == (anchor ?: target) }
                        val sourceIndex = objects.indexOfFirst { it.stableKey == moving }
                        if (anchor != null && sourceIndex in 0 until targetIndex) targetIndex - 1 else targetIndex
                    }
                    draft = draft.moveObject(moving, destination, index, grouped)
                }
            },
            onDrop = {
                edgeDirection = 0
                onCommit(draft)
            },
            onCancel = {
                edgeDirection = 0
                draft = beforeDrag
            },
            onDragPosition = { point ->
                edgeDirection = when {
                    point.x < edge -> -1
                    point.x > viewportWidth - edge -> 1
                    else -> 0
                }
            },
            overlay = { moving ->
                shown.flatMap { it.objects }.firstOrNull { it.stableKey == moving }?.let { item ->
                    MixedShelfObject(item, booksById, Modifier.fillMaxSize(), onOpen, onDecorationClick, flatTop = true)
                }
            },
        ) {
            Cabinet3d(
                height = height,
                mixed = true,
                footer = {
                    CabinetPagerControls(
                        page = pager.currentPage,
                        pages = pages,
                        onPrevious = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    )
                },
            ) {
                val cabinetWidth = maxWidth
                val cabinetHeight = maxHeight
                HorizontalPager(state = pager, userScrollEnabled = drag.activeKey == null, modifier = Modifier.fillMaxSize()) { page ->
                    Box(Modifier.fillMaxSize()) {
                        repeat(MIXED_ROWS_PER_PAGE) { row ->
                            val lineIndex = page * MIXED_ROWS_PER_PAGE + row
                            val line = lines.getOrNull(lineIndex)
                            val rowHeight = cabinetHeight * (mixedRowBottoms[row] - mixedRowTops[row])
                            if (line != null) {
                                Row(
                                    Modifier.offset(cabinetWidth * ((1f - MIXED_ROW_WIDTH) / 2f), cabinetHeight * mixedRowTops[row])
                                        .size(cabinetWidth * MIXED_ROW_WIDTH, rowHeight)
                                        .then(drag.targetModifier("zone:${line.logicalShelf}:$lineIndex")),
                                    horizontalArrangement = if (line.groups.size > 1) Arrangement.SpaceBetween else Arrangement.Center,
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    val unit = (
                                        (
                                            cabinetWidth.value * MIXED_ROW_WIDTH - (line.groups.size - 1).coerceAtLeast(
                                                0,
                                            ) * 3f
                                            ).coerceAtLeast(0f) /
                                            line.groups.sumOf { it.units.toDouble() }.toFloat().coerceAtLeast(1f)
                                        ).coerceAtMost(53f)
                                    line.groups.forEach { group ->
                                        Column(Modifier.width((group.units * unit).dp), verticalArrangement = Arrangement.Bottom) {
                                            val hasOrnament = group.objects.size > 1 && group.objects.first() is ShelfObject.Decor
                                            val stackSize = group.objects.count { it.isHorizontalBook() }.coerceAtLeast(1)
                                            group.objects.asReversed().forEachIndexed { index, item ->
                                                key(item.stableKey) {
                                                    val book = item as? ShelfObject.Book
                                                    val variation = (item.stableKey.hashCode() ushr 1) % 7
                                                    val objectHeight = when (book?.placement?.orientation) {
                                                        BookOrientation.STACKED -> minOf(
                                                            (32 + variation).dp,
                                                            rowHeight * (if (hasOrnament) .53f else 1f) / stackSize,
                                                        )
                                                        BookOrientation.UPRIGHT -> rowHeight * (.92f + variation * .012f)
                                                        null -> rowHeight * (if (hasOrnament) .47f else .94f)
                                                    }
                                                    MixedShelfObject(
                                                        item,
                                                        booksById,
                                                        Modifier.fillMaxWidth().height(
                                                            objectHeight,
                                                        ).then(drag.itemModifier(item.stableKey)),
                                                        onOpen,
                                                        onDecorationClick,
                                                        flatTop = index == 0,
                                                        onToggle = { id -> onCommit(draft.toggleOrientation(id)) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MixedShelfObject(
    item: ShelfObject,
    booksById: Map<String, LocalBook>,
    modifier: Modifier,
    onOpen: (LocalBook) -> Unit,
    onDecorationClick: (String) -> Unit,
    flatTop: Boolean,
    onToggle: (String) -> Unit = {},
) {
    when (item) {
        is ShelfObject.Decor -> Image(
            painterResource(item.decoration.type.drawableRes),
            contentDescription = androidx.compose.ui.res.stringResource(item.decoration.type.labelRes),
            modifier = modifier.clickable { onDecorationClick(item.decoration.id) },
            alignment = Alignment.BottomCenter,
            contentScale = ContentScale.Fit,
        )
        is ShelfObject.Book -> booksById[item.placement.bookId]?.let { book ->
            val palette = mixedBookPalette
            val base = palette[(book.id.hashCode() and Int.MAX_VALUE) % palette.size]
            val upright = item.placement.orientation == BookOrientation.UPRIGHT
            Box(
                modifier.combinedClickable(onClick = { onOpen(book) }, onDoubleClick = { onToggle(book.id) }).semantics {
                    contentDescription =
                        book.title
                },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (upright) {
                    UprightBookVolume(book, base, Modifier.fillMaxSize())
                } else {
                    StackedBookVolume(book, base, flatTop, Modifier.fillMaxSize())
                }
            }
        }
    }
}
