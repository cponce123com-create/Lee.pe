package pe.lee.core.domain

/** Storage and backup limits shared by local editors and the backup reader. */
object LocalTextLimits {
    const val BOOK_TITLE = 500
    const val COLLECTION_NAME = 120
    const val ANNOTATION_TEXT = 12_000
    const val LOCATOR_JSON = 16_000
}
