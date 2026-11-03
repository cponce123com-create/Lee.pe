package pe.lee.app.ui.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.ui.reader.PdfReaderViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PdfReaderScreen(
    uri: Uri,
    documentUri: Uri,
    title: String,
    viewModel: PdfReaderViewModel,
    settings: SettingsRepository,
    onReadingVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val annotations by viewModel.annotationState.collectAsStateWithLifecycle()
    val dialogState = remember(uri) { PdfReaderDialogsState() }
    var showPageDialog by dialogState.showPageDialog
    var showPdfSearch by dialogState.showPdfSearch
    var pdfSearchQuery by dialogState.pdfSearchQuery
    var pdfSearchResults by dialogState.pdfSearchResults
    var pdfSearchMessage by dialogState.pdfSearchMessage
    var searchingPdf by dialogState.searchingPdf
    var pdfSearchJob by dialogState.pdfSearchJob
    var showAnnotations by dialogState.showAnnotations
    var showNoteEditor by dialogState.showNoteEditor
    var selectedPdfText by dialogState.selectedPdfText
    var selectedPdfPage by dialogState.selectedPdfPage
    var selectedPdfRects by dialogState.selectedPdfRects
    var showSelectionDialog by dialogState.showSelectionDialog
    var quoteForNote by dialogState.quoteForNote
    var editingNote by dialogState.editingNote
    var noteDraft by dialogState.noteDraft
    var requestedPage by dialogState.requestedPage
    val screenState = remember(uri) { PdfReaderScreenState(uri, settings) }
    var handle by screenState.handle
    var pageCount by screenState.pageCount
    var continuousScroll by screenState.continuousScroll
    var pageIndex by screenState.pageIndex
    var sliderPage by screenState.sliderPage
    var bitmap by screenState.bitmap
    var zoomScale by screenState.zoomScale
    var rotationDegrees by screenState.rotationDegrees
    var panOffset by screenState.panOffset
    var loading by screenState.loading
    var rendering by screenState.rendering
    var errorMessage by screenState.errorMessage
    var readerControlsVisible by screenState.readerControlsVisible
    var selectionMode by screenState.selectionMode
    val renderMutex = remember { Mutex() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current


    LaunchedEffect(viewModel, uri) { viewModel.openBook(uri.toString()) }

    // Do not count reading time on the error screen or while the PDF is loading (A-28).
    LaunchedEffect(loading, errorMessage) {
        onReadingVisibilityChange(!loading && errorMessage == null)
    }

    LaunchedEffect(pageIndex) { panOffset = Offset.Zero }

    // Persist the view state per book: zoom and rotation used to reset on every
    // rotation of the device (A-04).
    LaunchedEffect(zoomScale, rotationDegrees) {
        settings.apply(SettingsRepository.Store.LIBRARY) {
            putFloat("pdf-zoom:$uri", zoomScale)
            putInt("pdf-rotation:$uri", rotationDegrees)
        }
    }

    LaunchedEffect(uri) {
        try {
            val opened = openPdf(context, documentUri)
            handle = opened
            pageCount = opened.renderer.pageCount
            val savedBook = viewModel.findBook(uri.toString())
            viewModel.markOpened(uri.toString(), System.currentTimeMillis())
            val legacyPageKey = "pdf-page:$uri"
            val savedPage = if (settings.contains(SettingsRepository.Store.LIBRARY, legacyPageKey)) {
                settings.getInt(SettingsRepository.Store.LIBRARY, legacyPageKey, 0)
            } else {
                savedBook?.pdfPageIndex ?: 0
            }
            val restoredPage = savedPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            pageIndex = restoredPage
            sliderPage = restoredPage.toFloat()
            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message ?: "No se pudo abrir el PDF."
            loading = false
        }
    }

    // Bitmaps are released by the garbage collector once the new page replaces them.
    // Recycling them here would race with the frame Compose may still be drawing
    // ("Canvas: trying to use a recycled bitmap").
    DisposableEffect(handle) {
        onDispose {
            handle?.let {
                runCatching { it.renderer.close() }
                it.cachedCopy?.delete()
            }
        }
    }

    LaunchedEffect(handle, pageIndex, pageCount, continuousScroll) {
        val current = handle ?: return@LaunchedEffect
        if (pageCount == 0 || continuousScroll) return@LaunchedEffect
        rendering = true
        try {
            val rendered = renderMutex.withLock {
                withContext(Dispatchers.IO) {
                    val page = current.renderer.openPage(pageIndex)
                    try {
                        val maxWidthPx = with(density) { 900.dp.roundToPx() }
                        val size = pdfBitmapSize(page.width, page.height, maxWidthPx)
                        Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } finally {
                        page.close()
                    }
                }
            }
            bitmap = rendered
            settings.apply(SettingsRepository.Store.LIBRARY) { putInt("pdf-page:$uri", pageIndex) }
            val progress = if (pageCount <= 1) 0f else pageIndex.toFloat() / (pageCount - 1)
            viewModel.saveProgress(
                uri = uri.toString(),
                pageIndex = pageIndex,
                progress = progress,
                openedAt = System.currentTimeMillis()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message ?: "No se pudo renderizar esta página."
        } finally {
            rendering = false
        }
    }

    LaunchedEffect(continuousScroll, loading, pageCount) {
        if (!continuousScroll || loading || pageCount == 0) return@LaunchedEffect
        listState.scrollToItem(pageIndex.coerceIn(0, pageCount - 1))
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { visiblePage ->
                val currentPage = visiblePage.coerceIn(0, pageCount - 1)
                pageIndex = currentPage
                sliderPage = currentPage.toFloat()
                val currentProgress = if (pageCount <= 1) 0f else currentPage.toFloat() / (pageCount - 1)
                settings.apply(SettingsRepository.Store.LIBRARY) { putInt("pdf-page:$uri", currentPage) }
                viewModel.saveProgress(
                    uri = uri.toString(),
                    pageIndex = currentPage,
                    progress = currentProgress,
                    openedAt = System.currentTimeMillis()
                )
            }
    }

    fun moveToPage(index: Int) {
        val targetPage = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        pageIndex = targetPage
        sliderPage = targetPage.toFloat()
        if (continuousScroll && pageCount > 0) {
            coroutineScope.launch { listState.animateScrollToItem(targetPage) }
        }
    }

    fun searchPdf(query: String) {
        val term = query.trim()
        if (term.isEmpty()) {
            pdfSearchResults = emptyList()
            pdfSearchMessage = "Escribe una palabra o frase para buscar."
            return
        }
        if (Build.VERSION.SDK_INT < 35) {
            pdfSearchResults = emptyList()
            pdfSearchMessage = "La búsqueda de PDF requiere Android 15 (API 35) o posterior."
            return
        }
        val current = handle ?: return
        pdfSearchResults = emptyList()
        pdfSearchMessage = "Buscando en el documento…"
        pdfSearchJob = coroutineScope.launch {
            searchingPdf = true
            try {
                val outcome = searchPdfDocument(current.renderer, pageCount, term, renderMutex)
                pdfSearchResults = outcome.results
                pdfSearchMessage = outcome.message
            } catch (cancelled: CancellationException) {
                pdfSearchMessage = "Búsqueda cancelada."
                throw cancelled
            } catch (error: Exception) {
                pdfSearchMessage = error.message?.let { "No se pudo buscar en el PDF: $it" }
                    ?: "No se pudo buscar en el PDF."
            } finally {
                searchingPdf = false
                pdfSearchJob = null
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111F))
    ) {
        val availableWidth = with(density) { maxWidth.roundToPx() - 32.dp.roundToPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            PdfReaderTopBar(
                visible = readerControlsVisible,
                title = title,
                loading = loading,
                pageCount = pageCount,
                onBack = onBack,
                onSearch = { showPdfSearch = true },
                onAnnotations = { showAnnotations = true },
                onHideControls = { readerControlsVisible = false }
            )

            PdfPageViewport(
                state = screenState,
                dialogs = dialogState,
                listState = listState,
                renderMutex = renderMutex,
                coroutineScope = coroutineScope,
                context = context,
                annotations = annotations,
                availableWidth = availableWidth
            )

            PdfReaderBottomControls(
                visible = readerControlsVisible,
                pageCount = pageCount,
                pageIndex = pageIndex,
                continuousScroll = continuousScroll,
                selectionMode = selectionMode,
                sliderPage = sliderPage,
                zoomScale = zoomScale,
                onSelectionModeToggle = {
                    selectionMode = !selectionMode
                    selectedPdfText = null
                    if (!selectionMode) showSelectionDialog = false
                    zoomScale = 1f
                    rotationDegrees = 0
                    panOffset = Offset.Zero
                },
                onContinuousScrollChange = { enabled ->
                    continuousScroll = enabled
                    if (enabled) selectionMode = false
                    settings.apply(SettingsRepository.Store.LIBRARY) {
                        putBoolean("pdf-continuous:$uri", enabled)
                    }
                },
                onSliderChange = { sliderPage = it },
                onSliderFinished = { moveToPage(sliderPage.roundToInt()) },
                onZoomOut = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f); panOffset = Offset.Zero },
                onZoomReset = { zoomScale = 1f; panOffset = Offset.Zero },
                onZoomIn = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3f); panOffset = Offset.Zero },
                onRotate = { rotationDegrees = (rotationDegrees + 90) % 360; panOffset = Offset.Zero },
                onPrevious = { moveToPage(pageIndex - 1) },
                onRequestPage = {
                    requestedPage = (pageIndex + 1).toString()
                    showPageDialog = true
                },
                onNext = { moveToPage(pageIndex + 1) }
            )
        }

        PdfReaderRestoreControls(
            visible = readerControlsVisible,
            onShowControls = { readerControlsVisible = true },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        PdfReaderDialogs(
            state = dialogState,
            context = context,
            uri = uri,
            bookTitle = title,
            pageIndex = pageIndex,
            pageCount = pageCount,
            annotations = annotations,
            viewModel = viewModel,
            coroutineScope = coroutineScope,
            moveToPage = ::moveToPage,
            searchPdf = ::searchPdf
        )

        errorMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = Color(0xFF15253A),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("No se pudo abrir el PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(message, color = Color(0xFFD3DCEC))
                    Button(onClick = onBack) { Text("Volver a la biblioteca") }
                }
            }
        }

    }
}
