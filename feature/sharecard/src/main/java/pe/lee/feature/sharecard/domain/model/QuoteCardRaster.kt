package pe.lee.feature.sharecard.domain.model

/** A row-major ARGB raster captured from the Compose card preview. */
data class QuoteCardRaster(val width: Int, val height: Int, val argbPixels: IntArray) {
    init {
        require(width > 0 && height > 0)
        require(argbPixels.size == width * height)
    }
}
