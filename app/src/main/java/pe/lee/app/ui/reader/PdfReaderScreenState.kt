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

    // Zoom and rotation are per book, so a rotation of the device (or reopening
    // the PDF) keeps the exact view the reader left (A-04).
    val zoomScale = mutableFloatStateOf(
        settings.getFloat(SettingsRepository.Store.LIBRARY, "pdf-zoom:$uri", 1f).coerceIn(1f, 3f)
    )
    val rotationDegrees = mutableIntStateOf(
        normalizedRotation(
            settings.getInt(SettingsRepository.Store.LIBRARY, "pdf-rotation:$uri", 0)
        )
    )
    val panOffset = mutableStateOf(Offset.Zero)
    val loading = mutableStateOf(true)
    val rendering = mutableStateOf(false)
    val errorMessage = mutableStateOf<String?>(null)
    val readerControlsVisible = mutableStateOf(true)
    val selectionMode = mutableStateOf(false)
}

/** Keeps persisted rotation values on 0/90/180/270. */
private fun normalizedRotation(value: Int): Int =
    ((((value % 360) + 360) % 360) / 90) * 90
