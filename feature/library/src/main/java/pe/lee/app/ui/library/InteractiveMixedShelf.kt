package pe.lee.app.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class MixedShelfGroup(val objects: List<ShelfObject>, val units: Float)
internal data class MixedShelfLine(val logicalShelf: Int, val groups: List<MixedShelfGroup>)

/** Capacity is measured before layout so every visual line gets its own supporting plank. */
internal fun packMixedShelves(shelves: List<ShelfDisplay>, capacity: Float): List<MixedShelfLine> = buildList {
    shelves.forEach { shelf ->
        val groups = mutableListOf<MixedShelfGroup>()
        shelf.objects.forEach { item ->
            val stacked = item is ShelfObject.Book && item.placement.orientation == BookOrientation.STACKED
            val previous = groups.lastOrNull()
            if (stacked && previous != null && previous.objects.size < 3 && previous.objects.all {
                    it is ShelfObject.Book && it.placement.orientation == BookOrientation.STACKED
                }) {
                groups[groups.lastIndex] = previous.copy(objects = previous.objects + item)
            } else {
                groups += MixedShelfGroup(listOf(item), if (stacked) 2f else if (item is ShelfObject.Decor) 1.7f else .82f)
            }
        }
        var line = mutableListOf<MixedShelfGroup>()
        var used = 0f
        groups.forEach { group ->
            if (line.isNotEmpty() && used + group.units > capacity) {
                add(MixedShelfLine(shelf.index, line.toList()))
                line = mutableListOf()
                used = 0f
            }
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
        val availableWidth = minOf(maxWidth.value, maxHeight.value / 1.5f) * .84f
        val capacity = (availableWidth / 45f).coerceAtLeast(2f)
        val shown = (0 until draft.shelfCount()).mapNotNull { shelf ->
            val objects = draft.objectsOnShelf(shelf).filter { it !is ShelfObject.Book || it.placement.bookId in visibleIds }
            objects.takeIf { it.isNotEmpty() }?.let { ShelfDisplay(shelf, it) }
        }
        val lines = packMixedShelves(shown, capacity).let { packed ->
            val emptyShelf = beforeDrag.shelfCount()
            if (drag.activeKey != null && shown.none { it.index == emptyShelf }) {
                packed + MixedShelfLine(emptyShelf, emptyList())
            } else {
                packed
            }
        }
        val pages = ((lines.size + 2) / 3).coerceAtLeast(1)
        val pager = rememberPagerState(pageCount = { pages })
        val scope = rememberCoroutineScope()
        LaunchedEffect(drag.activeKey, edgeDirection) {
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
                val destination = if (target.startsWith("zone:")) {
                    target.substringAfter("zone:").substringBefore(':').toIntOrNull()
                } else {
                    (0 until draft.shelfCount()).firstOrNull { shelf ->
                        draft.objectsOnShelf(shelf).any { it.stableKey == target }
                    }
                }
                if (destination != null) {
                    val objects = draft.objectsOnShelf(destination)
                    val index = if (target.startsWith("zone:")) objects.size else objects.indexOfFirst { it.stableKey == target }
                    draft = draft.moveObject(moving, destination, index)
                }
            },
            onDrop = { edgeDirection = 0; onCommit(draft) },
            onCancel = { edgeDirection = 0; draft = beforeDrag },
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
                        page = pager.currentPage, pages = pages,
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
                                    val unit = ((availableWidth - line.groups.size * 3f) /
                                        line.groups.sumOf { it.units.toDouble() }.toFloat().coerceAtLeast(1f)).coerceAtMost(45f)
                                    line.groups.forEach { group ->
                                        Column(Modifier.width((group.units * unit).dp), verticalArrangement = Arrangement.Bottom) {
                                            group.objects.asReversed().forEachIndexed { index, item ->
                                                key(item.stableKey) {
                                                    val objectHeight = if (group.objects.size > 1 && index > 0) {
                                                        rowHeight * .22f
                                                    } else {
                                                        rowHeight * (1f - (group.objects.size - 1) * .22f)
                                                    }
                                                    MixedShelfObject(
                                                        item, booksById,
                                                        Modifier.fillMaxWidth().height(objectHeight).then(drag.itemModifier(item.stableKey)),
                                                        onOpen, onDecorationClick, flatTop = index == 0,
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
                modifier.combinedClickable(onClick = { onOpen(book) }, onDoubleClick = { onToggle(book.id) }).semantics { contentDescription = book.title },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (upright) {
                    Box(
                        Modifier.fillMaxSize().padding(top = 5.dp).shadow(5.dp, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(Brush.horizontalGradient(listOf(base.copy(alpha = .78f), base, Color(0xFF183B35)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.fillMaxWidth().height(5.dp).align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color(0xFFE7D2A8), Color(0xFFFFF0CF)))))
                        Canvas(Modifier.fillMaxSize()) {
                            val depth = 5.dp.toPx()
                            val top = 7.dp.toPx()
                            val pages = Path().apply {
                                moveTo(0f, top)
                                lineTo(depth, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width - depth, top)
                                close()
                            }
                            drawPath(pages, Color(0xFFF8E9C8))
                            drawLine(
                                Color(0xFFCAB48A), Offset(depth, top / 2f), Offset(size.width - depth / 2f, top / 2f),
                                strokeWidth = 1.dp.toPx(),
                            )
                            val side = Path().apply {
                                moveTo(size.width - depth, top)
                                lineTo(size.width, 0f)
                                lineTo(size.width, size.height - depth)
                                lineTo(size.width - depth, size.height)
                                close()
                            }
                            drawPath(side, Color(0xFF173C36))
                        }
                        Box(Modifier.width(2.dp).fillMaxHeight().align(Alignment.CenterStart).background(Color(0x55FFFFFF)))
                        Text(
                            book.title.replace('_', ' '),
                            modifier = Modifier.requiredWidth(116.dp).graphicsLayer { rotationZ = 270f },
                            color = Color(0xFFFFF1D6), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        if (flatTop) {
                            ShelfCoverArt(book, Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                                rotationX = 50f
                                cameraDistance = 18f * density
                                transformOrigin = TransformOrigin(.5f, 1f)
                            }, portrait = false)
                        }
                        Text(
                            book.title.replace('_', ' '),
                            Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(3.dp)).background(base, RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 4.dp),
                            color = Color(0xFFFFF1D6), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
