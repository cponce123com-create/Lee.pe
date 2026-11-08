package pe.lee.app.ui.library

/**
 * Finds possible duplicates by normalized title and format.
 * This is intentionally a suggestion, not proof of identical file contents.
 * Never delete books automatically based on this heuristic.
 */
internal fun findPossibleDuplicateBooks(books: List<LocalBook>): List<List<LocalBook>> =
    books.groupBy { normalizedLibraryText(it.title) to it.format.uppercase(java.util.Locale.ROOT) }
        .filterKeys { (title, _) -> title.isNotBlank() }
        .values.filter { it.size > 1 }
        .sortedByDescending { it.size }
