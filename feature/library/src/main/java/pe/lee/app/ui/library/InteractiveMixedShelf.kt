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

/** Capacity is measured before layout so every visual line gets its own supporting plank. */
internal fun packMixedShelves(shelves: List<ShelfDisplay>, capacity: Float, gapUnits: Float = 0f): List<MixedShelfLine> = buildList {
    shelves.forEach { shelf ->
        val groups = mutableListOf<MixedShelfGroup>()
        shelf.objects.forEach { item ->
            val stacked = item is ShelfObject.Book && item.placement.orientation == BookOrientation.STACKED
            val previous = groups.lastOrNull()
            if (stacked &&
                previous != null &&
                previous.objects.size < 3 &&
                previous.objects.all {
                    it is ShelfObject.Book && it.placement.orientation == BookOrientation.STACKED
                }
            ) {
                groups[groups.lastIndex] = previous.copy(objects = previous.objects + item)
            } else {
                groups += MixedShelfGroup(
                    listOf(item),
                    if (stacked) {
                        2f
                    } else if (item is ShelfObject.Decor) {
                        1.7f
                    } else {
                        .82f
                    },
                )
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
        val availableWidth = minOf(maxWidth.value, maxHeight.value / 1.5f) * .83f
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
        val pages = ((lines.size + 2) / 3).coerceAtLeast(1)
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
                    val index = if (target.startsWith("zone:")) objects.size else objects.indexOfFirst { it.stableKey == target }
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
                        repeat(3) { row ->
                            val lineIndex = page * 3 + row
                            val line = lines.getOrNull(lineIndex)
                            val rowHeight = cabinetHeight * (cabinetRowBottoms[row] - cabinetRowTops[row])
                            if (line != null) {
                                Row(
                                    Modifier.offset(cabinetWidth * .085f, cabinetHeight * cabinetRowTops[row])
                                        .size(cabinetWidth * .83f, rowHeight)
                                        .then(drag.targetModifier("zone:${line.logicalShelf}:$lineIndex")),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    val unit = (
                                        (cabinetWidth.value * .83f - (line.groups.size - 1).coerceAtLeast(0) * 3f).coerceAtLeast(0f) /
                                            line.groups.sumOf { it.units.toDouble() }.toFloat().coerceAtLeast(1f)
                                        ).coerceAtMost(45f)
                                    line.groups.forEach { group ->
                                        Column(Modifier.width((group.units * unit).dp), verticalArrangement = Arrangement.Bottom) {
                                            group.objects.asReversed().forEachIndexed { index, item ->
                                                key(item.stableKey) {
                                                    val objectHeight = if (group.objects.size > 1 && index > 0) {
                                                        rowHeight * .28f
                                                    } else {
                                                        rowHeight * (1f - (group.objects.size - 1) * .28f)
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
            val palette = listOf(Color(0xFF28654A), Color(0xFF245474), Color(0xFF64405D), Color(0xFFAA752D), Color(0xFF315A53))
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
