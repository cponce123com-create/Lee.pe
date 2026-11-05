package pe.lee.app.ui.library

import androidx.annotation.StringRes
import pe.lee.core.domain.BackupArchiveException
import pe.lee.core.domain.BackupCipherException
import pe.lee.feature.library.R

/** Translate only at the presentation boundary; never expose exception text. */
@StringRes
internal fun backupErrorMessage(error: Exception, @StringRes fallback: Int): Int = when (error) {
    is BackupCipherException -> when (error.reason) {
        BackupCipherException.Reason.INVALID_PASSWORD_OR_DATA -> R.string.backup_password_or_data_invalid
        BackupCipherException.Reason.UNSUPPORTED_VERSION -> R.string.backup_version_unsupported
        BackupCipherException.Reason.PASSWORD_REQUIRED -> R.string.backup_password_required
        BackupCipherException.Reason.ENCRYPTION_FAILED -> R.string.library_export_backup_failed
    }
    is BackupArchiveException -> when (error.reason) {
        BackupArchiveException.Reason.SIZE_LIMIT -> R.string.backup_size_limit
        BackupArchiveException.Reason.RECORD_LIMIT -> R.string.backup_record_limit
        BackupArchiveException.Reason.UNSUPPORTED_FORMAT -> R.string.backup_version_unsupported
        BackupArchiveException.Reason.INVALID_DATA -> R.string.library_backup_invalid
        BackupArchiveException.Reason.SOURCE_UNAVAILABLE -> R.string.backup_source_unavailable
        BackupArchiveException.Reason.DESTINATION_UNAVAILABLE -> R.string.backup_destination_unavailable
    }
    else -> fallback
}
