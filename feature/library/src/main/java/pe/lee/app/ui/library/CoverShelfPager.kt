package pe.lee.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.lee.feature.library.R

internal const val COVER_BOOKS_PER_PAGE = 9

/** Fixed 3 × 3 slots; incomplete pages retain the same book dimensions. */
@Composable
internal fun CoverShelfPager(
    books: List<LocalBook>,
    height: Dp,
    savingBookUri: String?,
    onBookAction: (LocalBook, LibraryBookAction) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val drag = remember { ShelfDragState() }
    var draft by remember { mutableStateOf(books) }
    var beforeDrag by remember { mutableStateOf(books) }
    var widthPx by remember { mutableStateOf(0f) }
    var edge by remember { mutableStateOf(0) }
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    val pages = ((draft.size + COVER_BOOKS_PER_PAGE - 1) / COVER_BOOKS_PER_PAGE).coerceAtLeast(1)
    val pager = rememberPagerState(pageCount = { pages })
    LaunchedEffect(books) { if (drag.activeKey == null) draft = books }
    LaunchedEffect(books.map { it.id }.toSet()) { pager.scrollToPage(0) }
    LaunchedEffect(drag.activeKey, edge) {
        while (drag.activeKey != null && edge != 0) {
            delay(650)
            val target = (pager.currentPage + edge).coerceIn(0, pages - 1)
            if (target != pager.currentPage) pager.animateScrollToPage(target)
        }
    }
    ShelfDragSurface(
        state = drag,
        modifier = Modifier.fillMaxWidth().height(height).onGloballyPositioned { widthPx = it.size.width.toFloat() },
        onStart = { beforeDrag = draft },
        onHover = { moving, target ->
            val sourceIndex = draft.indexOfFirst { "book:${it.id}" == moving }
            val targetIndex = draft.indexOfFirst { "book:${it.id}" == target }
            if (sourceIndex >= 0 && targetIndex >= 0) {
                draft = draft.toMutableList().apply { add(targetIndex, removeAt(sourceIndex)) }
            }
        },
        onDrop = { edge = 0; onReorder(draft.map { it.id }) },
        onCancel = { edge = 0; draft = beforeDrag },
        onDragPosition = { point ->
            edge = when {
                point.x < edgePx -> -1
                point.x > widthPx - edgePx -> 1
                else -> 0
            }
        },
        overlay = { key ->
            draft.firstOrNull { "book:${it.id}" == key }?.let { ShelfCoverArt(it, Modifier.fillMaxSize()) }
        },
    ) {
        val scope = rememberCoroutineScope()
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
            HorizontalPager(
                state = pager,
                userScrollEnabled = drag.activeKey == null,
                modifier = Modifier.fillMaxSize().testTag("cover-pager"),
            ) { page ->
                Box(Modifier.fillMaxSize()) {
                    repeat(3) { row ->
                        Row(
                            Modifier.offset(cabinetWidth * .155f, cabinetHeight * cabinetRowTops[row])
                                .size(cabinetWidth * .69f, cabinetHeight * (cabinetRowBottoms[row] - cabinetRowTops[row])),
                            horizontalArrangement = Arrangement.spacedBy(cabinetWidth * .035f),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            repeat(3) { column ->
                                val book = draft.getOrNull(page * COVER_BOOKS_PER_PAGE + row * 3 + column)
                                if (book == null) {
                                    Spacer(Modifier.weight(1f).fillMaxHeight())
                                } else {
                                    key(book.id) {
                                        Box(
                                            Modifier.weight(1f).fillMaxHeight()
                                                .then(drag.itemModifier("book:${book.id}")).testTag("cover-book-${book.id}"),
                                        ) {
                                            ShelfCoverArt(
                                                book,
                                                Modifier.fillMaxSize().clickable { onBookAction(book, LibraryBookAction.OPEN) },
                                                stand = true,
                                            )
                                            BookActions(
                                                book = book,
                                                savingBookUri = savingBookUri,
                                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                                    .background(Color(0xB33A2418), RoundedCornerShape(8.dp)),
                                                onAction = { onBookAction(book, it) },
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

/** One paper-backed cover, without a metadata card or synthetic purple frame. */
@Composable
internal fun ShelfCoverArt(book: LocalBook, modifier: Modifier = Modifier, portrait: Boolean = true, stand: Boolean = false) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomCenter) {
        val width = if (portrait) minOf(maxWidth.value, maxHeight.value * .68f) else maxWidth.value
        val height = if (portrait) width / .68f else maxHeight.value
        ShelfCoverFace(book, Modifier.size(width.dp, height.dp))
        if (stand) {
            Box(
                Modifier.align(Alignment.BottomCenter).size(width.dp, 5.dp)
                    .shadow(2.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFE7B66A), Color(0xFF543219), Color(0xFFAA7437))),
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
            )
        }
    }
}

@Composable
private fun ShelfCoverFace(book: LocalBook, modifier: Modifier) {
    Box(
        modifier.padding(start = 2.dp, end = 3.dp, top = 3.dp, bottom = 2.dp)
            .semantics { contentDescription = book.title }
            .shadow(5.dp, RoundedCornerShape(3.dp))
            .background(Color(0xFFE0C9A4), RoundedCornerShape(3.dp)).padding(end = 3.dp, bottom = 2.dp),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationY = -3f; cameraDistance = 24f * density }
                .clip(RoundedCornerShape(2.dp)).background(Color(0xFFFFFCF4)),
            contentAlignment = Alignment.Center,
        ) {
            when (book.format) {
                "EPUB" -> LocalEpubCover(book.uri, book.offlineCopyUri, Modifier.fillMaxSize())
                "PDF" -> LocalPdfCover(book.uri, book.offlineCopyUri, Modifier.fillMaxSize())
                else -> Text(book.title, modifier = Modifier.padding(6.dp), color = Color(0xFF174E44), fontSize = 12.sp)
            }
        }
        Box(Modifier.width(3.dp).fillMaxHeight().background(Color(0x33000000)))
        if (book.readProgress > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(book.readProgress.coerceIn(0f, 1f)).height(2.dp).background(Color(0xFF075B50)))
        }
    }
}

@Composable
internal fun ShelfPlank(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(11.dp).shadow(5.dp, RoundedCornerShape(2.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFFFFDCA4), Color(0xFFD69B56), Color(0xFFA36731))),
                RoundedCornerShape(2.dp),
            ),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFFFECC8)))
    }
}

@Composable
internal fun CabinetPagerControls(page: Int, pages: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val pageDescription = stringResource(R.string.shelf_page_position, page + 1, pages)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = onPrevious, enabled = page > 0, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cabinet_previous_page))
            }
            Text(
                if (maxWidth < 120.dp) "${page + 1} / $pages" else pageDescription,
                color = Color(0xFFFFE6B8), fontSize = 9.sp, maxLines = 1,
                modifier = Modifier.weight(1f).semantics { contentDescription = pageDescription },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onNext, enabled = page + 1 < pages, modifier = Modifier.size(28.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.cabinet_next_page))
            }
        }
    }
}
