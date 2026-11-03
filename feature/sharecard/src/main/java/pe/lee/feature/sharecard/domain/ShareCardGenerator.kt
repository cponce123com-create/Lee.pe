package pe.lee.feature.sharecard.domain

import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.domain.model.QuoteCardRaster

/** Encodes a captured quote card into a PNG image at the requested social format. */
fun interface ShareCardGenerator {
    suspend fun generate(
        card: QuoteCard,
        raster: QuoteCardRaster,
        format: QuoteCardFormat
    ): ByteArray
}
