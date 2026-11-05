package pe.lee.app.ui

import java.text.NumberFormat
import java.util.Locale

internal fun formatPlaybackSpeed(speed: Float): String {
    return NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PE")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }.format(speed)
}
