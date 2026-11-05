package pe.lee.core.domain

import java.io.IOException

/** Stable backup failures, without UI language or private record contents. */
class BackupArchiveException(val reason: Reason, cause: Throwable? = null) : IOException(reason.name, cause) {
    enum class Reason {
        SIZE_LIMIT,
        RECORD_LIMIT,
        UNSUPPORTED_FORMAT,
        INVALID_DATA,
        SOURCE_UNAVAILABLE,
        DESTINATION_UNAVAILABLE,
    }
}
