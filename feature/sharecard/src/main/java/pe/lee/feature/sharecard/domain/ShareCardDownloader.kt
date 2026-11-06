package pe.lee.feature.sharecard.domain

/** Saves generated PNG bytes to a destination selected by the user. */
fun interface ShareCardDownloader {
    suspend fun download(pngBytes: ByteArray, destination: String)
}
