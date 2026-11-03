package pe.lee.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import pe.lee.app.ui.audio.AudioPlayerViewModel
import pe.lee.app.ui.library.LibraryViewModel
import pe.lee.app.ui.reader.EpubReaderViewModel
import pe.lee.app.ui.reader.PdfReaderViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
            LibraryViewModel(container.books, container.annotations, container.readingStats) as T
        modelClass.isAssignableFrom(EpubReaderViewModel::class.java) ->
            EpubReaderViewModel(container.books, container.annotations) as T
        modelClass.isAssignableFrom(PdfReaderViewModel::class.java) ->
            PdfReaderViewModel(container.books, container.annotations) as T
        modelClass.isAssignableFrom(AudioPlayerViewModel::class.java) -> AudioPlayerViewModel() as T
        else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
    }
}
