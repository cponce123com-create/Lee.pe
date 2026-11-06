package pe.lee.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf

internal class EpubPageTurnFrame(
    val bitmap: Bitmap,
    val isForward: Boolean,
    val paperColor: Int,
    val style: PageTurnStyle,
    interactive: Boolean = false,
) {
    val progress = mutableFloatStateOf(0f)
    val phase = mutableStateOf(if (interactive) PageTurnPhase.DRAGGING else PageTurnPhase.COMPLETING)
}

internal enum class PageTurnPhase { DRAGGING, NAVIGATING, COMPLETING, CANCELLING }
