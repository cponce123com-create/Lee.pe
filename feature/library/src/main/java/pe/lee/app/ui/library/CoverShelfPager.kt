package pe.lee.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = drag.activeKey == null,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("cover-pager"),
            ) { page ->
                Column(
                    Modifier.fillMaxSize().shadow(5.dp, RoundedCornerShape(10.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFD59A5B), Color(0xFFF5DEC0), Color(0xFFD59A5B))))
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                ) {
                    repeat(3) { row ->
                        Column(Modifier.weight(1f).fillMaxWidth()) {
                            Row(
                                Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF6EAD7)).padding(horizontal = 7.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                                )
                                                BookActions(
                                                    book = book,
                                                    savingBookUri = savingBookUri,
                                                    modifier = Modifier.align(Alignment.BottomEnd).size(26.dp)
                                                        .background(Color(0xDFFFF8EF), RoundedCornerShape(8.dp)),
                                                    onAction = { onBookAction(book, it) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ShelfPlank()
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.shelf_page_position, pager.currentPage + 1, pages),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
            )
        }
    }
}

/** One paper-backed cover, without a metadata card or synthetic purple frame. */
@Composable
internal fun ShelfCoverArt(book: LocalBook, modifier: Modifier = Modifier, portrait: Boolean = true) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomCenter) {
        val width = if (portrait) minOf(maxWidth.value, maxHeight.value * .68f) else maxWidth.value
        val height = if (portrait) width / .68f else maxHeight.value
        ShelfCoverFace(book, Modifier.size(width.dp, height.dp))
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
