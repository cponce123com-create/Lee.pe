package pe.lee.app.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders only the first page of VISIBLE local PDF books, on Dispatchers.IO.
 * The tiny bitmap is cached in RAM; no server, external image library, network
 * permission or full-document rendering is required.
 */
private object PdfThumbnailCache {
    private const val MAX_WIDTH = 160
    private const val MAX_HEIGHT = 220
    private const val MAX_CACHE_KIB = 6 * 1024

    private val thumbnails = object : LruCache<String, Bitmap>(MAX_CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val failed = object : LruCache<String, Boolean>(256) {}

    fun load(context: Context, uri: Uri): Bitmap? {
        val key = uri.toString()
        synchronized(thumbnails) {
            thumbnails.get(key)?.let { return it }
            if (failed.get(key) == true) return null
        }
        val result = runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@runCatching null
            descriptor.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount <= 0) return@use null
                    renderer.openPage(0).use { page ->
                        if (page.width <= 0 || page.height <= 0) return@use null
                        val ratio = min(
                            MAX_WIDTH.toFloat() / page.width,
                            MAX_HEIGHT.toFloat() / page.height
                        )
                        val width = (page.width * ratio).roundToInt().coerceIn(1, MAX_WIDTH)
                        val height = (page.height * ratio).roundToInt().coerceIn(1, MAX_HEIGHT)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            Canvas(bitmap).drawColor(AndroidColor.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.getOrNull()
        synchronized(thumbnails) {
            if (result == null) failed.put(key, true) else thumbnails.put(key, result)
        }
        return result
    }
}

/**
 * A missing, password-protected, remote-only or invalid PDF falls back to a
 * built-in icon. Never show the thumbnail as if it came from a third-party API.
 */
@Composable
internal fun LocalPdfCover(
    originalUri: Uri,
    offlineCopyUri: Uri?,
    modifier: Modifier = Modifier
) {
    val sourceUri = offlineCopyUri ?: originalUri
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = sourceUri) {
        value = withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            PdfThumbnailCache.load(context.applicationContext, sourceUri)
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = Color.White
            )
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Vista previa local de la primera página PDF",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
