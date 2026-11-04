package pe.lee.app.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import pe.lee.app.R

/** Helpers for releasing SAF grants and warning before Android's persisted-grant limit. */
internal object PersistableUriGrants {
    fun release(context: Context, uri: Uri) {
        val permission = context.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (permission.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (permission.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags != 0) runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
    }

    fun warnIfNearLimit(context: Context) {
        val limit = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) 128 else 512
        val count = context.contentResolver.persistedUriPermissions.size
        if (count >= limit - 8) {
            Toast.makeText(
                context,
                context.getString(R.string.library_uri_permission_limit, count, limit),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
