package pe.lee.feature.sharecard.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.lee.feature.sharecard.domain.ShareCardGenerator
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.domain.model.QuoteCardRaster

/** Encodes pixels captured from the Compose quote-card surface as a 1080px PNG. */
class ComposeShareCardGenerator : ShareCardGenerator {
    override suspend fun generate(card: QuoteCard, raster: QuoteCardRaster, format: QuoteCardFormat): ByteArray =
        withContext(Dispatchers.Default) {
            require(card.text.isNotBlank()) { "La cita está vacía." }
            require(raster.width > 0 && raster.height > 0) { "La captura de la tarjeta está vacía." }
            require(raster.argbPixels.size >= raster.width * raster.height) {
                "Los píxeles capturados no coinciden con el tamaño de la tarjeta."
            }
            val source = Bitmap.createBitmap(
                raster.argbPixels,
                raster.width,
                raster.height,
                Bitmap.Config.ARGB_8888,
            )
            var output: Bitmap? = null
            try {
                output = Bitmap.createScaledBitmap(source, format.widthPx, format.heightPx, true)
                ByteArrayOutputStream().use { stream ->
                    check(output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "No se pudo codificar la imagen PNG."
                    }
                    stream.toByteArray()
                }
            } finally {
                // The scaled bitmap used to be created outside the try: if scaling
                // threw, the source bitmap leaked native memory and made the next
                // attempt more likely to hit the OOM (A-21).
                output?.let { if (it !== source) it.recycle() }
                source.recycle()
            }
        }
}
