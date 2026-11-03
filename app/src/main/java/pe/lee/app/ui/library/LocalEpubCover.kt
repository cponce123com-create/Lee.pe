package pe.lee.app.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import kotlin.coroutines.coroutineContext

/**
 * Extracts actual EPUB cover metadata through the project's existing Readium engine.
 * Does not download cover art or rely on a cover API. Only visible cards trigger work.
 */
private object EpubCoverCache {
    private val mutex = Mutex() // bound concurrent publications and bitmap decoding
    private val images = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val failures = object : LruCache<String, Boolean>(256) {}

    suspend fun get(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        mutex.withLock {
            coroutineContext.ensureActive()
            images.get(key)?.let { return@withLock it }
            if (failures.get(key) == true) return@withLock null
            val bitmap = try {
                val assetUrl = uri.toAbsoluteUrl() ?: return@withLock null
                val httpClient = DefaultHttpClient()
                val retriever = AssetRetriever(context.contentResolver, httpClient)
                val asset = retriever.retrieve(assetUrl).getOrElse {
                    failures.put(key, true)
                    return@withLock null
                }
                val parser = DefaultPublicationParser(
                    context = context,
                    httpClient = httpClient,
                    assetRetriever = retriever,
                    pdfFactory = null
                )
                val publication = PublicationOpener(parser)
                    .open(asset, allowUserInteraction = false).getOrElse {
                        asset.close()
                        failures.put(key, true)
                        return@withLock null
                    }
                try {
                    // Readium 3.2: Size is android.util.Size, not a custom type.
                    // The result can be null when the EPUB has no declared cover.
                    publication.coverFitting(Size(160, 220))
                } finally {
                    publication.close()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                images.put(key, bitmap)
            } else {
                failures.put(key, true)
            }
            bitmap
        }
    }
}

@Composable
internal fun LocalEpubCover(
    originalUri: Uri,
    offlineCopyUri: Uri?,
    modifier: Modifier = Modifier
) {
    val sourceUri = offlineCopyUri ?: originalUri
    val context = androidx.compose.ui.platform.LocalContext.current
    val cover by produceState<Bitmap?>(
        initialValue = null,
        key1 = sourceUri
    ) {
        value = EpubCoverCache.get(context.applicationContext, sourceUri)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (cover == null) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                tint = Color.White
            )
        } else {
            Image(
                bitmap = cover!!.asImageBitmap(),
                contentDescription = null, // parent's title describes the book
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
