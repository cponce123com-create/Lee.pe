package pe.lee.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pe.lee.app.ui.library.LibraryScreen
import pe.lee.app.ui.library.LibraryViewModel
import pe.lee.app.ui.theme.LeePeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LeePeApplication).container
        val libraryViewModel = ViewModelProvider(this, container.viewModelFactory)
            .get(LibraryViewModel::class.java)
        setContent {
            LeePeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LibraryScreen(libraryViewModel, container.settings)
                }
            }
        }
    }
}
