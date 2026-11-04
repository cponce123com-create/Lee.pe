package pe.lee.app.data.local

/** Flat Room projection used to expose every shelf assigned to a book. */
data class BookCollectionAssignment(
    val bookId: String,
    val collectionName: String
)
