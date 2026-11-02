package pe.lee.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pe.lee.app.ui.library.LibraryScreen
import pe.lee.app.ui.theme.LeePeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LeePeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LibraryScreen()
                }
            }
        }
    }
}
