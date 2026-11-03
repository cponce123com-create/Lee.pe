package pe.lee.app.ui.reader

import android.content.Context
import android.graphics.Point
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pe.lee.app.R
import pe.lee.core.domain.ReaderAnnotation
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun ColumnScope.PdfPageViewport(
    state: PdfReaderScreenState,
    dialogs: PdfReaderDialogsState,
    listState: LazyListState,
    renderMutex: Mutex,
    coroutineScope: CoroutineScope,
    context: Context,
    annotations: List<ReaderAnnotation>,
    availableWidth: Int
) {
    var handle by state.handle
    var pageCount by state.pageCount
    var continuousScroll by state.continuousScroll
    var pageIndex by state.pageIndex
    var bitmap by state.bitmap
    var zoomScale by state.zoomScale
    var rotationDegrees by state.rotationDegrees
    var panOffset by state.panOffset
    var loading by state.loading
    var rendering by state.rendering
    var errorMessage by state.errorMessage
    var readerControlsVisible by state.readerControlsVisible
    var selectionMode by state.selectionMode
    var selectedPdfText by dialogs.selectedPdfText
    var selectedPdfPage by dialogs.selectedPdfPage
    var selectedPdfRects by dialogs.selectedPdfRects
    var showSelectionDialog by dialogs.showSelectionDialog
    val pdfSearchResults by dialogs.pdfSearchResults
    val pageHighlights = androidx.compose.runtime.remember(annotations, pageIndex) {
        pdfHighlightOverlays(annotations, pageIndex)
    }
    val pageSearchHighlights = androidx.compose.runtime.remember(pdfSearchResults, pageIndex) {
        pdfSearchResults.asSequence().filter { it.pageIndex == pageIndex }
            .flatMap { it.rects.asSequence() }
            .distinct()
            .map { rect -> PdfHighlightOverlay(rect, PDF_SEARCH_HIGHLIGHT_COLOR) }
            .toList()
    }
            if (continuousScroll) {
                if (loading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                } else if (pageCount == 0) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.pdf_no_pages), color = Color.White)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(count = pageCount, key = { pageNumber -> "pdf-page-$pageNumber" }) { pageNumber ->
                            PdfContinuousPage(
                                renderer = handle?.renderer,
                                renderMutex = renderMutex,
                                pageIndex = pageNumber,
                                targetWidthPx = availableWidth.coerceAtLeast(1).coerceAtMost(1600),
                                highlights = pdfHighlightOverlays(annotations, pageNumber) +
                                    pdfSearchResults.filter { it.pageIndex == pageNumber }
                                        .flatMap { result -> result.rects }
                                        .distinct()
                                        .map { rect -> PdfHighlightOverlay(rect, PDF_SEARCH_HIGHLIGHT_COLOR) }
                            )
                        }
                    }
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val imageModifier = if (rotationDegrees % 180 != 0) {
                        Modifier.requiredSize(width = maxHeight, height = maxWidth)
                    } else {
                        Modifier.fillMaxSize()
                    }
                    bitmap?.let { pageBitmap ->
                        Image(
                            bitmap = pageBitmap.asImageBitmap(),
                            contentDescription = stringResource(
                                R.string.pdf_page_rotation_content_description,
                                pageIndex + 1,
                                rotationDegrees
                            ),
                            modifier = imageModifier
                                .clipToBounds()
                                .clickable(enabled = !selectionMode) {
                                    readerControlsVisible = !readerControlsVisible
                                }
                                .pointerInput(pageIndex, rotationDegrees, selectionMode, zoomScale) {
                                    if (selectionMode) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            var end = down.position
                                            var pressed = true
                                            while (pressed) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                end = change.position
                                                pressed = change.pressed
                                                change.consume()
                                            }
                                            val current = handle
                                            val pageBitmap = bitmap
                                            if (Build.VERSION.SDK_INT >= 35 &&
                                                current != null &&
                                                pageBitmap != null &&
                                                rotationDegrees == 0 &&
                                                zoomScale == 1f
                                            ) {
                                                val fit = min(
                                                    size.width.toFloat() / pageBitmap.width,
                                                    size.height.toFloat() / pageBitmap.height
                                                )
                                                val displayedWidth = pageBitmap.width * fit
                                                val displayedHeight = pageBitmap.height * fit
                                                val left = (size.width - displayedWidth) / 2f
                                                val top = (size.height - displayedHeight) / 2f
                                                fun pageRatio(position: Offset): Offset? {
                                                    if (position.x !in left..(left + displayedWidth) ||
                                                        position.y !in top..(top + displayedHeight)
                                                    ) return null
                                                    return Offset(
                                                        ((position.x - left) / displayedWidth).coerceIn(0f, 1f),
                                                        ((position.y - top) / displayedHeight).coerceIn(0f, 1f)
                                                    )
                                                }
                                                val startRatio = pageRatio(down.position)
                                                val endRatio = pageRatio(end)
                                                if (startRatio != null && endRatio != null) {
                                                    coroutineScope.launch {
                                                        val text = runCatching {
                                                            renderMutex.withLock {
                                                                withContext(Dispatchers.IO) {
                                                                    val page = current.renderer.openPage(pageIndex)
                                                                    try {
                                                                        val start = SelectionBoundary(
                                                                            Point(
                                                                                (startRatio.x * page.width).roundToInt().coerceIn(0, page.width - 1),
                                                                                (startRatio.y * page.height).roundToInt().coerceIn(0, page.height - 1)
                                                                            )
                                                                        )
                                                                        val stop = SelectionBoundary(
                                                                            Point(
                                                                                (endRatio.x * page.width).roundToInt().coerceIn(0, page.width - 1),
                                                                                (endRatio.y * page.height).roundToInt().coerceIn(0, page.height - 1)
                                                                            )
                                                                        )
                                                                        page.selectContent(start, stop)?.let { selection ->
                                                                            val selectedText = selection.selectedTextContents
                                                                                .joinToString(separator = "") { it.text }
                                                                                .trim()
                                                                            val rects = selection.selectedTextContents
                                                                                .flatMap { it.bounds }
                                                                                .map { bounds ->
                                                                                    PdfNormalizedRect(
                                                                                        left = (bounds.left / page.width).coerceIn(0f, 1f),
                                                                                        top = (bounds.top / page.height).coerceIn(0f, 1f),
                                                                                        right = (bounds.right / page.width).coerceIn(0f, 1f),
                                                                                        bottom = (bounds.bottom / page.height).coerceIn(0f, 1f)
                                                                                    )
                                                                                }
                                                                            if (selectedText.isNotEmpty()) PdfSelectedText(selectedText, rects) else null
                                                                        }
                                                                    } finally {
                                                                        page.close()
                                                                    }
                                                                }
                                                            }
                                                        }.getOrNull()?.takeIf { it.text.isNotEmpty() }
                                                        if (text != null) {
                                                            selectedPdfText = text.text
                                                            selectedPdfRects = text.rects
                                                            selectedPdfPage = pageIndex
                                                            showSelectionDialog = true
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                context.getString(R.string.pdf_no_text_detected),
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        detectTransformGestures { _, pan, zoomChange, _ ->
                                            val nextScale = (zoomScale * zoomChange).coerceIn(1f, 3f)
                                            zoomScale = nextScale
                                            if (nextScale == 1f) {
                                                panOffset = Offset.Zero
                                            } else {
                                                val maxX = size.width * (nextScale - 1f) / 2f
                                                val maxY = size.height * (nextScale - 1f) / 2f
                                                panOffset = Offset(
                                                    (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                                    (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                                )
                                            }
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    rotationZ = rotationDegrees.toFloat()
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                },
                            contentScale = ContentScale.Fit
                        )
                        val currentPageOverlays = pageHighlights + pageSearchHighlights
                        if (currentPageOverlays.isNotEmpty()) {
                            Canvas(
                                modifier = imageModifier
                                    .clipToBounds()
                                    .graphicsLayer {
                                        rotationZ = rotationDegrees.toFloat()
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = panOffset.x
                                        translationY = panOffset.y
                                    }
                            ) {
                                val fit = min(
                                    size.width / pageBitmap.width,
                                    size.height / pageBitmap.height
                                )
                                val displayedWidth = pageBitmap.width * fit
                                val displayedHeight = pageBitmap.height * fit
                                val left = (size.width - displayedWidth) / 2f
                                val top = (size.height - displayedHeight) / 2f
                                currentPageOverlays.forEach { highlight ->
                                    val rect = highlight.rect
                                    drawRect(
                                        color = Color(highlight.colorArgb).copy(alpha = 0.40f),
                                        topLeft = androidx.compose.ui.geometry.Offset(
                                            left + rect.left * displayedWidth,
                                            top + rect.top * displayedHeight
                                        ),
                                        size = Size(
                                            (rect.right - rect.left) * displayedWidth,
                                            (rect.bottom - rect.top) * displayedHeight
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (loading || rendering) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                    if (pageCount == 0 && !loading && errorMessage == null) {
                        Text(stringResource(R.string.pdf_no_pages), color = Color.White)
                    }
                }
            }

}
