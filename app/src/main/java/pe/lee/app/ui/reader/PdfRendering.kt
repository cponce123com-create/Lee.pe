package pe.lee.app.ui.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.app.R
import pe.lee.core.domain.ReaderAnnotation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.roundToInt

internal fun pdfPageLocator(pageIndex: Int): String =
    JSONObject().put("format", "pdf-page").put("pageIndex", pageIndex).toString()

internal data class PdfNormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal data class PdfSelectedText(val text: String, val rects: List<PdfNormalizedRect>)

internal data class PdfSearchResult(val pageIndex: Int, val text: String, val rects: List<PdfNormalizedRect>)

internal data class PdfHighlightOverlay(
    val rect: PdfNormalizedRect,
    val colorArgb: Int
)

internal fun pdfTextLocator(pageIndex: Int, rects: List<PdfNormalizedRect>): String =
    JSONObject().apply {
        put("format", "pdf-text")
        put("pageIndex", pageIndex)
        put("rects", JSONArray().apply {
            rects.forEach { rect ->
                put(JSONObject()
                    .put("left", rect.left)
                    .put("top", rect.top)
                    .put("right", rect.right)
                    .put("bottom", rect.bottom))
            }
        })
    }.toString()

internal fun pdfHighlightOverlays(
    annotations: List<ReaderAnnotation>,
    pageIndex: Int
): List<PdfHighlightOverlay> = annotations
    .filter { it.kind == "HIGHLIGHT" }
    .flatMap { annotation ->
        runCatching {
            val json = JSONObject(annotation.locatorJson)
            if (json.optInt("pageIndex", -1) != pageIndex) return@runCatching emptyList()
            val storedRects = json.optJSONArray("rects") ?: return@runCatching emptyList()
            (0 until storedRects.length()).mapNotNull { index ->
                val rect = storedRects.optJSONObject(index) ?: return@mapNotNull null
                PdfHighlightOverlay(
                    rect = PdfNormalizedRect(
                        left = rect.optDouble("left").toFloat().coerceIn(0f, 1f),
                        top = rect.optDouble("top").toFloat().coerceIn(0f, 1f),
                        right = rect.optDouble("right").toFloat().coerceIn(0f, 1f),
                        bottom = rect.optDouble("bottom").toFloat().coerceIn(0f, 1f)
                    ),
                    colorArgb = annotation.colorArgb ?: DEFAULT_PDF_HIGHLIGHT_COLOR
                )
            }
        }.getOrDefault(emptyList())
    }

internal const val DEFAULT_PDF_HIGHLIGHT_COLOR = 0xFFFFD54F.toInt()
internal const val PDF_SEARCH_HIGHLIGHT_COLOR = 0xFF42A5F5.toInt()
internal const val MAX_PDF_BITMAP_PIXELS = 4_194_304L

/** Copies abandoned by a killed process are removed before the next open. */
private const val STALE_COPY_AGE_MS = 7L * 24L * 60L * 60L * 1000L

private fun purgeStaleCopies(context: Context) {
    val cutoff = System.currentTimeMillis() - STALE_COPY_AGE_MS
    context.cacheDir?.listFiles { file ->
        file.isFile &&
            file.name.startsWith("lee-pe-") &&
            file.name.endsWith(".pdf") &&
            file.lastModified() < cutoff
    }?.forEach { stale -> runCatching { stale.delete() } }
}

internal data class PdfBitmapSize(val width: Int, val height: Int)

internal fun pdfBitmapSize(
    pageWidth: Int,
    pageHeight: Int,
    maxWidthPx: Int,
    maxHeightPx: Int? = null,
    maxPixels: Long = MAX_PDF_BITMAP_PIXELS
): PdfBitmapSize {
    require(pageWidth > 0 && pageHeight > 0) { "El PDF contiene una página sin dimensiones válidas." }
    val sourcePixels = pageWidth.toLong() * pageHeight.toLong()
    val budget = maxPixels.coerceAtLeast(1L)
    val memoryScale = sqrt(budget.toDouble() / sourcePixels).toFloat()
    val widthScale = maxWidthPx.coerceAtLeast(1).toFloat() / pageWidth
    val heightScale = maxHeightPx?.let { it.coerceAtLeast(1).toFloat() / pageHeight }
        ?: Float.POSITIVE_INFINITY
    val scale = minOf(widthScale, heightScale, 2f, memoryScale)

    var width = (pageWidth * scale).roundToInt().coerceAtLeast(1)
    var height = (pageHeight * scale).roundToInt().coerceAtLeast(1)
    while (width.toLong() * height.toLong() > budget) {
        if (width >= height && width > 1) width-- else if (height > 1) height--
    }
    return PdfBitmapSize(width, height)
}

/**
 * A page bitmap costs four bytes per pixel. On a low-RAM device the previous
 * fixed budget (4 Mpx = 16 MB) could hold several pages at once and end in an
 * OutOfMemoryError while scrolling (P-05).
 */
internal fun pdfPixelBudget(context: Context): Long =
    if (context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true) {
        LOW_RAM_PDF_BITMAP_PIXELS
    } else {
        MAX_PDF_BITMAP_PIXELS
    }

internal const val LOW_RAM_PDF_BITMAP_PIXELS = 2_097_152L

internal data class PdfHandle(
    val renderer: PdfRenderer,
    val cachedCopy: File?
)

@Composable
internal fun PdfContinuousPage(
    renderer: PdfRenderer?,
    renderMutex: Mutex,
    pageIndex: Int,
    targetWidthPx: Int,
    highlights: List<PdfHighlightOverlay>
) {
    var bitmap by remember(renderer, pageIndex, targetWidthPx) { mutableStateOf<Bitmap?>(null) }
    var renderFailed by remember(renderer, pageIndex, targetWidthPx) { mutableStateOf(false) }
    val appContext = LocalContext.current.applicationContext
    val pixelBudget = remember(appContext) { pdfPixelBudget(appContext) }

    LaunchedEffect(renderer, pageIndex, targetWidthPx, pixelBudget) {
        val currentRenderer = renderer ?: return@LaunchedEffect
        renderFailed = false
        try {
            bitmap = renderMutex.withLock {
                withContext(Dispatchers.IO) {
                    val page = currentRenderer.openPage(pageIndex)
                    try {
                        val size = pdfBitmapSize(
                            page.width,
                            page.height,
                            maxWidthPx = targetWidthPx,
                            maxHeightPx = 2200,
                            maxPixels = pixelBudget
                        )
                        Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also {
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

    // No manual recycle(): the page bitmap is replaced while Compose may still be
    // drawing the previous frame, and recycling it there crashes the render thread.
    bitmap?.let { pageBitmap ->
        Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
            Image(
                bitmap = pageBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.pdf_page_number, pageIndex + 1),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            if (highlights.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    highlights.forEach { highlight ->
                        val rect = highlight.rect
                        drawRect(
                            color = Color(highlight.colorArgb).copy(alpha = 0.40f),
                            topLeft = Offset(
                                rect.left * size.width,
                                rect.top * size.height
                            ),
                            size = Size(
                                (rect.right - rect.left) * size.width,
                                (rect.bottom - rect.top) * size.height
                            )
                        )
                    }
                }
            }
        }
    } ?: Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        if (renderFailed) {
            Text(stringResource(R.string.pdf_render_page_failed, pageIndex + 1), color = Color.White)
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
        }
    }
}

internal suspend fun openPdf(context: Context, uri: Uri): PdfHandle = withContext(Dispatchers.IO) {
    purgeStaleCopies(context)
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IOException(context.getString(R.string.pdf_open_provider_failed))
    val sourceSizeBytes = runCatching { descriptor.statSize }.getOrDefault(-1L)

    try {
        PdfHandle(PdfRenderer(descriptor), null)
    } catch (error: IllegalArgumentException) {
        runCatching { descriptor.close() }
        val copy = File.createTempFile("lee-pe-", ".pdf", context.cacheDir)
        try {
            val availableBytes = StatFs(context.cacheDir.path).availableBytes
            if (sourceSizeBytes > 0L && sourceSizeBytes > availableBytes) {
                throw IOException(context.getString(R.string.pdf_open_no_space))
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copiedBytes = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (copiedBytes + count > availableBytes) {
                            throw IOException(context.getString(R.string.pdf_open_no_space))
                        }
                        output.write(buffer, 0, count)
                        copiedBytes += count
                    }
                }
            } ?: throw IOException(context.getString(R.string.pdf_open_read_failed))
            coroutineContext.ensureActive()
            val copyDescriptor = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
            val copyRenderer = try {
                PdfRenderer(copyDescriptor)
            } catch (copyError: Exception) {
                runCatching { copyDescriptor.close() }
                throw copyError
            }
            PdfHandle(copyRenderer, copy)
        } catch (cancelled: CancellationException) {
            copy.delete()
            throw cancelled
        } catch (copyError: Exception) {
            copy.delete()
            throw IOException(
                context.getString(
                    R.string.pdf_open_local_copy_required,
                    copyError.message ?: error.message
                )
            )
        }
    } catch (error: Exception) {
        runCatching { descriptor.close() }
        throw IOException(context.getString(R.string.pdf_open_failed_detail, error.message))
    }
}
