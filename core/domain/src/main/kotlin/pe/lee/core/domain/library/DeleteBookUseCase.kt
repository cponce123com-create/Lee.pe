package pe.lee.core.domain.library

/** Delegates deletion to the store's transaction, including its durable cleanup queue. */
class DeleteBookUseCase(private val delete: suspend (String) -> Unit) {
    /** Deletes by document identity. Storage errors and coroutine cancellation propagate. */
    suspend operator fun invoke(uri: String) {
        require(uri.isNotBlank()) { "Book identity must not be blank" }
        delete(uri)
    }
}
