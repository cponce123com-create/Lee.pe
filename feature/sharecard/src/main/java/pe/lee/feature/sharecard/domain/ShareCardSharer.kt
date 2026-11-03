package pe.lee.feature.sharecard.domain

/** Shares PNG bytes through the platform share sheet. */
fun interface ShareCardSharer {
    suspend fun share(pngBytes: ByteArray)
}
