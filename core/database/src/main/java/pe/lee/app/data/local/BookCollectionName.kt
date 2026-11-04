package pe.lee.app.data.local

/** Flat Room projection used to expose every shelf assigned to a book. */
data class BookCollectionName(
    val bookId: String,
    val collectionName: String
)
