package pe.lee.app.ui.reader

import android.content.Context
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.ComponentActivity
import pe.lee.app.data.local.LeeDatabase
import pe.lee.app.data.local.ReadingAnnotationEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

private const val PDF_PREFS_NAME = "lee_pe_library"

private fun pdfPageLocator(pageIndex: Int): String =
    JSONObject().put("format", "pdf-page").put("pageIndex", pageIndex).toString()

class PdfReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Documento PDF"
        setContent {
            pe.lee.app.ui.theme.LeePeTheme {
                PdfReaderScreen(uri = uri, title = title, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "pe.lee.app.pdf.TITLE"
    }
}

private data class PdfHandle(
    val renderer: PdfRenderer,
    val cachedCopy: File?
)

@Composable
private fun PdfReaderScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { LeeDatabase.get(context) }
    val bookDao = remember(database) { database.bookDao() }
    val annotationDao = remember(database) { database.readingAnnotationDao() }
    val annotations by annotationDao.observeByBook(uri.toString()).collectAsState(initial = emptyList())
    var handle by remember { mutableStateOf<PdfHandle?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var continuousScroll by remember(uri) {
        mutableStateOf(context.getSharedPreferences(PDF_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("pdf-continuous:$uri", false))
    }
    var pageIndex by remember { mutableIntStateOf(0) }
    var sliderPage by remember { mutableFloatStateOf(0f) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoomScale by remember(uri) { mutableFloatStateOf(1f) }
    var rotationDegrees by remember(uri) { mutableIntStateOf(0) }
    var panOffset by remember(uri) { mutableStateOf(Offset.Zero) }
    var loading by remember { mutableStateOf(true) }
    var rendering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPageDialog by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<ReadingAnnotationEntity?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var requestedPage by remember { mutableStateOf("1") }
    val renderMutex = remember { Mutex() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(pageIndex) { panOffset = Offset.Zero }

    LaunchedEffect(uri) {
        try {
            val opened = openPdf(context, uri)
            handle = opened
            pageCount = opened.renderer.pageCount
            val prefs = context.getSharedPreferences(PDF_PREFS_NAME, Context.MODE_PRIVATE)
            val savedBook = bookDao.findByUri(uri.toString())
            bookDao.markOpened(uri.toString(), System.currentTimeMillis())
            val legacyPageKey = "pdf-page:$uri"
            val savedPage = if (prefs.contains(legacyPageKey)) {
                prefs.getInt(legacyPageKey, 0)
            } else {
                savedBook?.pdfPageIndex ?: 0
            }
            val restoredPage = savedPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            pageIndex = restoredPage
            sliderPage = restoredPage.toFloat()
            loading = false
        } catch (error: Exception) {
            errorMessage = error.message ?: "No se pudo abrir el PDF."
            loading = false
        }
    }

    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
    }

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
                        val scale = min(maxWidthPx.toFloat() / page.width, 2.0f)
                        val targetWidth = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val targetHeight = (page.height * scale).roundToInt().coerceAtLeast(1)
                        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } finally {
                        page.close()
                    }
                }
            }
            bitmap = rendered
            context.getSharedPreferences(PDF_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("pdf-page:$uri", pageIndex)
                .apply()
            val progress = if (pageCount <= 1) 0f else pageIndex.toFloat() / (pageCount - 1)
            bookDao.savePdfProgress(
                uri = uri.toString(),
                pageIndex = pageIndex,
                progress = progress,
                openedAt = System.currentTimeMillis()
            )
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
                context.getSharedPreferences(PDF_PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt("pdf-page:$uri", currentPage).apply()
                bookDao.savePdfProgress(
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111F))
    ) {
        val availableWidth = with(density) { maxWidth.roundToPx() - 32.dp.roundToPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = Color(0xFF0A1423)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFF75DFFF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { showAnnotations = true }) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Marcadores y notas PDF", tint = Color.White)
                    }
                }
            }

            if (continuousScroll) {
                if (loading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                } else if (pageCount == 0) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Este PDF no contiene páginas.", color = Color.White)
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
                                targetWidthPx = availableWidth.coerceAtLeast(1).coerceAtMost(1600)
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
                            contentDescription = "Página ${pageIndex + 1}, rotación ${rotationDegrees} grados",
                            modifier = imageModifier
                                .clipToBounds()
                                .pointerInput(pageIndex, rotationDegrees) {
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
                                .graphicsLayer {
                                    rotationZ = rotationDegrees.toFloat()
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (loading || rendering) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                    if (pageCount == 0 && !loading && errorMessage == null) {
                        Text("Este PDF no contiene páginas.", color = Color.White)
                    }
                }
            }

            Surface(
                color = Color(0xFF0A1423),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Desplazamiento continuo", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = continuousScroll,
                            onCheckedChange = { enabled ->
                                continuousScroll = enabled
                                context.getSharedPreferences(PDF_PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit().putBoolean("pdf-continuous:$uri", enabled).apply()
                            }
                        )
                    }
                    if (pageCount > 1) {
                        Slider(
                            value = sliderPage,
                            onValueChange = { sliderPage = it },
                            onValueChangeFinished = { moveToPage(sliderPage.roundToInt()) },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!continuousScroll) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f)
                                    panOffset = Offset.Zero
                                },
                                enabled = zoomScale > 1f
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Reducir zoom", tint = Color.White)
                            }
                            TextButton(onClick = {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            }) {
                                Text("${(zoomScale * 100).toInt()}% · Restablecer", color = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale + 0.25f).coerceAtMost(3f)
                                    panOffset = Offset.Zero
                                },
                                enabled = zoomScale < 3f
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Ampliar zoom", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90) % 360
                                    panOffset = Offset.Zero
                                }
                            ) {
                                Icon(
                                    Icons.Default.RotateRight,
                                    contentDescription = "Rotar página",
                                    tint = Color.White
                                )
                            }
                        }
    
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { moveToPage(pageIndex - 1) }, enabled = pageIndex > 0) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Página anterior", tint = Color.White)
                        }
                        TextButton(
                            onClick = {
                                requestedPage = (pageIndex + 1).toString()
                                showPageDialog = true
                            }
                        ) {
                            Text(
                                if (pageCount > 0) "Página ${pageIndex + 1} de $pageCount" else "Preparando PDF",
                                color = Color.White
                            )
                        }
                        IconButton(onClick = { moveToPage(pageIndex + 1) }, enabled = pageIndex < pageCount - 1) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Página siguiente", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (showAnnotations) {
            ModalBottomSheet(onDismissRequest = { showAnnotations = false }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Notas y marcadores PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = {
                            editingNote = null
                            noteDraft = ""
                            showNoteEditor = true
                        }) { Text("Añadir nota") }
                        TextButton(onClick = {
                            val now = System.currentTimeMillis()
                            coroutineScope.launch {
                                annotationDao.upsert(
                                    ReadingAnnotationEntity(
                                        id = UUID.randomUUID().toString(),
                                        bookUri = uri.toString(),
                                        kind = "BOOKMARK",
                                        locatorJson = pdfPageLocator(pageIndex),
                                        createdAt = now
                                    )
                                )
                            }
                        }) { Text("Marcar página") }
                    }
                }
                if (annotations.isEmpty()) {
                    Text(
                        "Todavía no guardaste notas ni marcadores para este PDF.",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(annotations, key = { it.id }) { annotation ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).clickable {
                                        val targetPage = runCatching {
                                            JSONObject(annotation.locatorJson).optInt("pageIndex", -1)
                                        }.getOrDefault(-1)
                                        if (targetPage in 0 until pageCount) moveToPage(targetPage)
                                        showAnnotations = false
                                    }.padding(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        annotation.noteText?.takeIf { it.isNotBlank() }
                                            ?: if (annotation.kind == "NOTE") "Nota" else "Página marcada",
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Página ${runCatching { JSONObject(annotation.locatorJson).optInt("pageIndex", -1) + 1 }.getOrDefault(0)} · abrir",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                                if (annotation.kind == "NOTE") {
                                    IconButton(onClick = {
                                        editingNote = annotation
                                        noteDraft = annotation.noteText.orEmpty()
                                        showNoteEditor = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar nota")
                                    }
                                }
                                IconButton(onClick = {
                                    coroutineScope.launch { annotationDao.deleteById(annotation.id) }
                                }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar anotación")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (showNoteEditor) {
            AlertDialog(
                onDismissRequest = { showNoteEditor = false },
                title = { Text(if (editingNote == null) "Nueva nota PDF" else "Editar nota PDF") },
                text = {
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text("Nota vinculada a la página actual") },
                        minLines = 3,
                        maxLines = 6
                    )
                },
                confirmButton = {
                    TextButton(enabled = noteDraft.isNotBlank(), onClick = {
                        val currentEdit = editingNote
                        coroutineScope.launch {
                            if (currentEdit == null) {
                                val now = System.currentTimeMillis()
                                annotationDao.upsert(
                                    ReadingAnnotationEntity(
                                        id = UUID.randomUUID().toString(),
                                        bookUri = uri.toString(),
                                        kind = "NOTE",
                                        locatorJson = pdfPageLocator(pageIndex),
                                        noteText = noteDraft.trim(),
                                        createdAt = now
                                    )
                                )
                            } else {
                                annotationDao.updateNote(currentEdit.id, noteDraft.trim(), System.currentTimeMillis())
                            }
                        }
                        showNoteEditor = false
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showNoteEditor = false }) { Text("Cancelar") } }
            )
        }

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

        if (showPageDialog) {
            AlertDialog(
                onDismissRequest = { showPageDialog = false },
                title = { Text("Ir a una página") },
                text = {
                    TextField(
                        value = requestedPage,
                        onValueChange = { requestedPage = it.filter(Char::isDigit) },
                        label = { Text("Número de página") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        requestedPage.toIntOrNull()?.let { page ->
                            if (page in 1..pageCount) moveToPage(page - 1)
                        }
                        showPageDialog = false
                    }) { Text("Ir") }
                },
                dismissButton = {
                    TextButton(onClick = { showPageDialog = false }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
private fun PdfContinuousPage(
    renderer: PdfRenderer?,
    renderMutex: Mutex,
    pageIndex: Int,
    targetWidthPx: Int
) {
    var bitmap by remember(renderer, pageIndex, targetWidthPx) { mutableStateOf<Bitmap?>(null) }
    var renderFailed by remember(renderer, pageIndex, targetWidthPx) { mutableStateOf(false) }

    LaunchedEffect(renderer, pageIndex, targetWidthPx) {
        val currentRenderer = renderer ?: return@LaunchedEffect
        renderFailed = false
        try {
            bitmap = renderMutex.withLock {
                withContext(Dispatchers.IO) {
                    val page = currentRenderer.openPage(pageIndex)
                    try {
                        val widthLimit = targetWidthPx.coerceAtLeast(1)
                        val scale = min(
                            min(widthLimit.toFloat() / page.width, 2f),
                            2200f / page.height
                        )
                        val targetWidth = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val targetHeight = (page.height * scale).roundToInt().coerceAtLeast(1)
                        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } finally {
                        page.close()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            renderFailed = true
        }
    }

    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
    }

    bitmap?.let { pageBitmap ->
        Image(
            bitmap = pageBitmap.asImageBitmap(),
            contentDescription = "Página ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth().clipToBounds(),
            contentScale = ContentScale.FillWidth
        )
    } ?: Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        if (renderFailed) {
            Text("No se pudo renderizar la página ${pageIndex + 1}.", color = Color.White)
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        }
    }
}

private suspend fun openPdf(context: Context, uri: Uri): PdfHandle = withContext(Dispatchers.IO) {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IOException("El proveedor de archivos no pudo abrir el PDF.")

    try {
        PdfHandle(PdfRenderer(descriptor), null)
    } catch (error: IllegalArgumentException) {
        runCatching { descriptor.close() }
        val copy = File(context.cacheDir, "lee-pe-${uri.toString().hashCode()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("No se pudo leer el archivo seleccionado.")
            PdfHandle(
                PdfRenderer(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)),
                copy
            )
        } catch (copyError: Exception) {
            copy.delete()
            throw IOException("El PDF requiere un archivo local accesible: ${copyError.message ?: error.message}")
        }
    } catch (error: Exception) {
        runCatching { descriptor.close() }
        throw IOException("No se pudo abrir el PDF: ${error.message}")
    }
}
