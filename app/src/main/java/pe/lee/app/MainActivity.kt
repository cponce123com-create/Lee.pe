package pe.lee.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.ui.audio.AudioPlayerActivity
import pe.lee.app.ui.library.LibraryScreen
import pe.lee.app.ui.library.LibraryViewModel
import pe.lee.app.ui.reader.PdfReaderActivity
import pe.lee.app.ui.reader.ReaderActivity
import pe.lee.app.ui.theme.LeePeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paper = 0xFF2C1E18.toInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(paper),
            navigationBarStyle = SystemBarStyle.dark(paper),
        )
        val container = (application as LeePeApplication).container
        val libraryViewModel = ViewModelProvider(this, container.viewModelFactory)
            .get(LibraryViewModel::class.java)
        setContent {
            LeePeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF2C1E18)) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        settings = container.settings,
                        backupRepository = container.backup,
                        onOpenBook = { uri, format, title, offlineCopyUri ->
                            val activity: Class<*> = when (format) {
                                "Audio" -> AudioPlayerActivity::class.java
                                "PDF" -> PdfReaderActivity::class.java
                                else -> ReaderActivity::class.java
                            }
                            val titleExtra = when (format) {
                                "Audio" -> AudioPlayerActivity.EXTRA_TITLE
                                "PDF" -> PdfReaderActivity.EXTRA_TITLE
                                else -> ReaderActivity.EXTRA_TITLE
                            }
                            startActivity(
                                Intent(this, activity)
                                    .setData(uri)
                                    .putExtra(titleExtra, title)
                                    .putExtra(OfflineBookStore.EXTRA_COPY_URI, offlineCopyUri?.toString()),
                            )
                        },
                    )
                }
            }
        }
    }
}
