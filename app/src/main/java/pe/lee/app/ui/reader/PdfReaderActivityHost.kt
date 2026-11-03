package pe.lee.app.ui.reader

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import pe.lee.app.LeePeApplication
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.ui.reader.PdfReaderViewModel



class PdfReaderActivity : ComponentActivity() {
    private var readingSessionRecorder: ReadingSessionRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        val container = (application as LeePeApplication).container
        val readerViewModel = ViewModelProvider(this, container.viewModelFactory)
            .get(PdfReaderViewModel::class.java)
        readingSessionRecorder = ReadingSessionRecorder(
            bookUri = uri.toString(),
            repository = container.readingStats
        )
        val documentUri = intent.getStringExtra(OfflineBookStore.EXTRA_COPY_URI)?.let(Uri::parse) ?: uri
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Documento PDF"
        setContent {
            pe.lee.app.ui.theme.LeePeTheme {
                PdfReaderScreen(
                    uri = uri,
                    documentUri = documentUri,
                    title = title,
                    viewModel = readerViewModel,
                    settings = container.settings,
                    onReadingVisibilityChange = { active ->
                        if (active) readingSessionRecorder?.onResume()
                        else readingSessionRecorder?.onPause()
                    },
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

    companion object {
        const val EXTRA_TITLE = "pe.lee.app.pdf.TITLE"
    }
}
