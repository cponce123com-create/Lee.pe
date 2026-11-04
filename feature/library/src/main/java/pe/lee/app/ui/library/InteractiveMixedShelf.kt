package pe.lee.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                groups += MixedShelfGroup(listOf(item), if (stacked) 2f else if (item is ShelfObject.Decor) 1.2f else .82f)
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
    val list = rememberLazyListState()
    var scrollSpeed by remember { mutableStateOf(0f) }
    var viewportHeight by remember { mutableStateOf(0f) }
    val edge = with(LocalDensity.current) { 44.dp.toPx() }
    LaunchedEffect(layout, shelves) { if (drag.activeKey == null) draft = initial }
    LaunchedEffect(drag.activeKey, scrollSpeed) {
        while (drag.activeKey != null && scrollSpeed != 0f) {
            list.scrollBy(scrollSpeed)
            delay(16)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val capacity = ((maxWidth.value - 16f) / 48f).coerceAtLeast(2f)
        val shown = (0 until draft.shelfCount()).mapNotNull { shelf ->
            val objects = draft.objectsOnShelf(shelf).filter { it !is ShelfObject.Book || it.placement.bookId in visibleIds }
            objects.takeIf { it.isNotEmpty() }?.let { ShelfDisplay(shelf, it) }
        }
        val lines = packMixedShelves(shown, capacity)
        ShelfDragSurface(
            state = drag,
            modifier = Modifier.fillMaxSize().onGloballyPositioned { viewportHeight = it.size.height.toFloat() },
            onStart = { beforeDrag = draft },
            onHover = { moving, target ->
                val destination = (0 until draft.shelfCount()).firstOrNull { shelf ->
                    draft.objectsOnShelf(shelf).any { it.stableKey == target }
                }
                if (destination != null) {
                    val index = draft.objectsOnShelf(destination).indexOfFirst { it.stableKey == target }
                    draft = draft.moveObject(moving, destination, index)
                }
            },
            onDrop = { scrollSpeed = 0f; onCommit(draft) },
            onCancel = { scrollSpeed = 0f; draft = beforeDrag },
            onDragPosition = { point ->
                scrollSpeed = when {
                    point.y < edge -> -12f
                    point.y > viewportHeight - edge -> 12f
                    else -> 0f
                }
            },
            overlay = { moving ->
                shown.flatMap { it.objects }.firstOrNull { it.stableKey == moving }?.let { item ->
                    MixedShelfObject(item, booksById, Modifier.fillMaxSize(), onOpen, onDecorationClick, flatTop = true)
                }
            },
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = drag.activeKey == null,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
                    Column(
                        Modifier.fillMaxWidth().background(
                            Brush.verticalGradient(listOf(Color(0xFFFFF8EF), Color(0xFFF1DFC3))),
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        ).padding(top = 10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(154.dp).padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            val unit = ((maxWidth.value - 16f - line.groups.size * 3f) /
                                line.groups.sumOf { it.units.toDouble() }.toFloat()).coerceAtMost(48f)
                            line.groups.forEach { group ->
                                Column(Modifier.width((group.units * unit).dp), verticalArrangement = Arrangement.Bottom) {
                                    group.objects.asReversed().forEachIndexed { index, item ->
                                        key(item.stableKey) {
                                            MixedShelfObject(
                                                item, booksById,
                                                Modifier.fillMaxWidth()
                                                    .height(if (group.objects.size > 1 && index > 0) 25.dp else 140.dp - ((group.objects.size - 1) * 25).dp)
                                                    .then(drag.itemModifier(item.stableKey)),
                                                onOpen, onDecorationClick, flatTop = index == 0,
                                                onToggle = { id -> onCommit(draft.toggleOrientation(id)) },
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        ShelfPlank()
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
                        Box(Modifier.width(2.dp).fillMaxHeight().align(Alignment.CenterStart).background(Color(0x55FFFFFF)))
                        Text(
                            book.title,
                            modifier = Modifier.requiredWidth(116.dp).graphicsLayer { rotationZ = 270f },
                            color = Color(0xFFFFF1D6), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                        if (flatTop) {
                            ShelfCoverArt(book, Modifier.weight(1f).fillMaxWidth().graphicsLayer { rotationX = 22f })
                        }
                        Text(
                            book.title,
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
