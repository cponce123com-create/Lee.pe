package pe.lee.app.ui.reader

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.material.icons.filled.Pause
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModelProvider
import pe.lee.app.LeePeApplication
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.ui.reader.EpubReaderViewModel
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import pe.lee.app.ui.theme.LeePeTheme
import java.io.IOException

class ReaderActivity : FragmentActivity() {
    private var readingSessionRecorder: ReadingSessionRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The Readium navigator owns a Publication held in memory. Restoring the old
        // Fragment after rotation/process death would restore it without that Publication.
        // Recreate a clean navigator instead; its persisted locator and per-book settings
        // are restored from Room / preferences in EpubReaderScreen.
        super.onCreate(null)

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        val container = (application as LeePeApplication).container
        val readerViewModel = ViewModelProvider(this, container.viewModelFactory)
            .get(EpubReaderViewModel::class.java)

        readingSessionRecorder = ReadingSessionRecorder(
            bookUri = uri.toString(),
            repository = container.readingStats
        )
        val offlineCopyUri = intent.getStringExtra(OfflineBookStore.EXTRA_COPY_URI)?.let(Uri::parse)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Lee.pe"
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LeePeTheme {
                EpubReaderScreen(
                    uri = uri,
                    title = title,
                    viewModel = readerViewModel,
                    settings = container.settings,
                    openPublication = { openPublication(uri, offlineCopyUri) },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        readingSessionRecorder?.onResume()
    }

    override fun onPause() {
        readingSessionRecorder?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        readingSessionRecorder?.close()
        readingSessionRecorder = null
        super.onDestroy()
    }

    private suspend fun openPublication(uri: Uri, offlineCopyUri: Uri?): Publication = withContext(Dispatchers.IO) {
        suspend fun openFrom(sourceUri: Uri): Publication {
            val url: AbsoluteUrl = sourceUri.toAbsoluteUrl()
                ?: throw IOException("No se pudo acceder al archivo seleccionado.")

            val httpClient = DefaultHttpClient()
            val assetRetriever = AssetRetriever(contentResolver, httpClient)
            val asset = assetRetriever.retrieve(url).getOrElse {
                throw IOException("No se pudo leer el EPUB: ${it.message ?: "archivo inaccesible"}")
            }

            val parser = DefaultPublicationParser(
                context = this@ReaderActivity,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null
            )
            return PublicationOpener(parser).open(asset, allowUserInteraction = false).getOrElse {
                asset.close()
                throw IOException("El archivo no parece ser un EPUB compatible: ${it.message ?: "formato inválido"}")
            }
        }

        val candidates = listOfNotNull(offlineCopyUri, uri).distinct()
        var lastError: IOException? = null
        for (candidate in candidates) {
            try {
                return@withContext openFrom(candidate)
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("No se pudo abrir el EPUB.")
    }

    companion object {
        const val EXTRA_TITLE = "pe.lee.app.reader.TITLE"
        private const val PREFS_NAME = "lee_pe_library"
        private const val KEY_DOCUMENT_URIS = "document_uris"

        fun locatorPreferenceKey(uri: Uri): String = "locator:$uri"
        fun preferencesPreferenceKey(uri: Uri): String = "epub-preferences:$uri"
        fun brightnessPreferenceKey(uri: Uri): String = "reader-brightness:$uri"
        fun keepScreenOnPreferenceKey(uri: Uri): String = "keep-screen-on:$uri"
    }
}
