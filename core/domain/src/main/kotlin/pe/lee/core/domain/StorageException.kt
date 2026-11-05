package pe.lee.core.domain

import java.io.IOException

/** Storage failures carry codes, never UI language or document identifiers. */
class StorageException(val reason: Reason, cause: Throwable? = null) : IOException(reason.name, cause) {
    enum class Reason {
        DIRECTORY_UNAVAILABLE,
        INSUFFICIENT_SPACE,
        SOURCE_UNAVAILABLE,
        EMPTY_SOURCE,
        SOURCE_CHANGED,
        FINALIZATION_FAILED,
        INVALID_FOLDER,
        FOLDER_PERMISSION_REVOKED,
    }
}
