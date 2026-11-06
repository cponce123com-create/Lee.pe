package pe.lee.feature.sharecard.data

import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.lee.feature.sharecard.domain.ShareCardDownloader as ShareCardDownloaderContract

/** Writes an exported quote card to the document URI selected by the user. */
class ShareCardDownloader(context: Context) : ShareCardDownloaderContract {
    private val appContext = context.applicationContext

    override suspend fun download(pngBytes: ByteArray, destination: String) {
        require(pngBytes.isNotEmpty())
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(destination)
            val output = appContext.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Unable to open image destination")
            output.use { it.write(pngBytes) }
        }
    }
}
