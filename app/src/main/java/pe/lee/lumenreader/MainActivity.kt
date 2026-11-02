package pe.lee.lumenreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pe.lee.lumenreader.ui.library.LibraryScreen
import pe.lee.lumenreader.ui.theme.LumenReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumenReaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LibraryScreen()
                }
            }
        }
    }
}
