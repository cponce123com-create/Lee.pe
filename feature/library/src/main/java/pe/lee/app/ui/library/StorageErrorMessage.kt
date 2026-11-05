package pe.lee.app.ui.library

import androidx.annotation.StringRes
import pe.lee.core.domain.StorageException
import pe.lee.feature.library.R

@StringRes
internal fun storageErrorMessage(error: Exception, @StringRes fallback: Int): Int = when (error) {
    is StorageException -> when (error.reason) {
        StorageException.Reason.DIRECTORY_UNAVAILABLE -> R.string.storage_directory_unavailable
        StorageException.Reason.INSUFFICIENT_SPACE -> R.string.storage_insufficient_space
        StorageException.Reason.SOURCE_UNAVAILABLE -> R.string.storage_source_unavailable
        StorageException.Reason.EMPTY_SOURCE -> R.string.storage_empty_source
        StorageException.Reason.SOURCE_CHANGED -> R.string.storage_source_changed
        StorageException.Reason.FINALIZATION_FAILED -> R.string.storage_finalization_failed
        StorageException.Reason.INVALID_FOLDER -> R.string.storage_invalid_folder
        StorageException.Reason.FOLDER_PERMISSION_REVOKED -> R.string.storage_folder_permission_revoked
    }
    else -> fallback
}
