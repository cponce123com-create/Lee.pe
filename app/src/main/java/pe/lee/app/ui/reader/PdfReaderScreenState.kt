package pe.lee.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import pe.lee.app.data.repository.SettingsRepository
import android.net.Uri

internal class PdfReaderScreenState(uri: Uri, settings: SettingsRepository) {
    val handle = mutableStateOf<PdfHandle?>(null)
    val pageCount = mutableIntStateOf(0)
    val continuousScroll = mutableStateOf(
        settings.getBoolean(SettingsRepository.Store.LIBRARY, "pdf-continuous:$uri", false)
    )
    val pageIndex = mutableIntStateOf(0)
    val sliderPage = mutableFloatStateOf(0f)
    val bitmap = mutableStateOf<Bitmap?>(null)
    val zoomScale = mutableFloatStateOf(1f)
    val rotationDegrees = mutableIntStateOf(0)
    val panOffset = mutableStateOf(Offset.Zero)
    val loading = mutableStateOf(true)
    val rendering = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val readerControlsVisible = mutableStateOf(true)
    val selectionMode = mutableStateOf(false)
}
