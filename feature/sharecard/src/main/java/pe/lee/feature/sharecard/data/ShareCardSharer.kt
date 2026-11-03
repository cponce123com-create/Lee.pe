package pe.lee.feature.sharecard.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.ShareCardSharer as ShareCardSharerContract
import java.io.File
import java.io.IOException
import java.util.UUID

/** Writes a temporary PNG to the app cache and opens Android's native share sheet. */
class ShareCardSharer(context: Context) : ShareCardSharerContract {
    private val appContext = context.applicationContext

    override suspend fun share(pngBytes: ByteArray) {
        require(pngBytes.isNotEmpty()) { "La imagen está vacía." }
        val file = withContext(Dispatchers.IO) {
            val directory = File(appContext.cacheDir, QUOTE_CARD_DIRECTORY)
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("No se pudo preparar el espacio temporal para la tarjeta.")
            }
            directory.listFiles()
                .orEmpty()
                .filter { it.isFile && it.lastModified() < System.currentTimeMillis() - CACHE_MAX_AGE_MS }
                .forEach { it.delete() }
            File(directory, "leepe-${UUID.randomUUID()}.png").apply {
                writeBytes(pngBytes)
            }
        }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = PNG_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(appContext.contentResolver, "Tarjeta de Lee.pe", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, appContext.getString(R.string.share_card_chooser_title)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        withContext(Dispatchers.Main.immediate) { appContext.startActivity(chooser) }
    }

    private companion object {
        const val QUOTE_CARD_DIRECTORY = "quotecards"
        const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val PNG_MIME_TYPE = "image/png"
    }
}
