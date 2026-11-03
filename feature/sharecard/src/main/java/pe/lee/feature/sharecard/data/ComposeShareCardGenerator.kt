package pe.lee.feature.sharecard.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.lee.feature.sharecard.domain.ShareCardGenerator
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.domain.model.QuoteCardRaster
import java.io.ByteArrayOutputStream

/** Encodes pixels captured from the Compose quote-card surface as a 1080px PNG. */
class ComposeShareCardGenerator : ShareCardGenerator {
    override suspend fun generate(
        card: QuoteCard,
        raster: QuoteCardRaster,
        format: QuoteCardFormat
    ): ByteArray = withContext(Dispatchers.Default) {
        require(card.text.isNotBlank()) { "La cita está vacía." }
        val source = Bitmap.createBitmap(
            raster.argbPixels,
            raster.width,
            raster.height,
            Bitmap.Config.ARGB_8888
        )
        val output = Bitmap.createScaledBitmap(source, format.widthPx, format.heightPx, true)
        try {
            ByteArrayOutputStream().use { stream ->
                check(output.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "No se pudo codificar la imagen PNG."
                }
                stream.toByteArray()
            }
        } finally {
            if (output !== source) output.recycle()
            source.recycle()
        }
    }
}
