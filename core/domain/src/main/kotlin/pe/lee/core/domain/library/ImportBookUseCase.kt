package pe.lee.core.domain.library

/** Imports a batch once per document identity; persistence owns the atomic transaction. */
class ImportBookUseCase<T>(
    private val persist: suspend (List<T>) -> Unit,
    private val identity: (T) -> String,
) {
    /** Keeps the first entry for duplicate identities and rejects blank identities before writing. */
    suspend operator fun invoke(books: List<T>) {
        require(books.all { identity(it).isNotBlank() }) { "Book identity must not be blank" }
        if (books.isNotEmpty()) persist(books.distinctBy(identity))
    }
}
