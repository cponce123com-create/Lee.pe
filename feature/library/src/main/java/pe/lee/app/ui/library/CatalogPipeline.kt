package pe.lee.app.ui.library

/**
 * Catalog filtered, sorted and ranked once, away from the UI thread.
 *
 * With 10 000 books the index + filter + sort measured ~1 s of CPU on a JVM
 * (see docs/METRICAS.md); doing it inside composition blocked the main thread on
 * every Room emission (P-06).
 */
internal data class CatalogResult(
    val visible: List<LocalBook>,
    val ordered: List<LocalBook>,
    val continueReading: List<LocalBook>
)

internal fun computeCatalog(
    books: List<LocalBook>,
    query: String,
    filter: LibraryFilter,
    collection: String?,
    sortMode: LibrarySortMode,
    titleComparator: Comparator<String>
): CatalogResult {
    val normalizedQuery = normalizedLibraryText(query)
    // Normalize accents/format/collection once per book instead of on each keystroke.
    val searchIndex = books.associate { book ->
        book.uri.toString() to normalizedLibraryText(
            "${book.title} ${book.format} ${book.collectionNames.joinToString(" ")}"
        )
    }
    val visible = books.filter { book ->
        matchesNormalizedLibraryQuery(
            searchIndex[book.uri.toString()].orEmpty(),
            normalizedQuery
        ) && matchesCatalogFilter(
            format = book.format,
            readProgress = book.readProgress,
            isFavorite = book.isFavorite,
            hasOfflineCopy = book.offlineCopyUri != null,
            collectionNames = book.collectionNames,
            selectedCollection = collection,
            filter = filter
        )
    }
    val continueReading = books.asSequence()
        .filter { it.format != "Audio" && it.readProgress > 0f && it.readProgress < 1f }
        .sortedByDescending { it.lastOpenedAt ?: 0L }
        .take(8)
        .toList()
    val ordered = when (sortMode) {
        LibrarySortMode.CUSTOM -> visible
        LibrarySortMode.LAST_READ -> visible.sortedByDescending { it.lastOpenedAt ?: 0L }
        LibrarySortMode.IMPORT_DATE -> visible.sortedByDescending { it.importedAt }
        LibrarySortMode.PROGRESS -> visible.sortedByDescending { it.readProgress }
        LibrarySortMode.TITLE_DESC ->
            visible.sortedWith(compareByDescending(titleComparator) { it.title })
        LibrarySortMode.TITLE_ASC ->
            visible.sortedWith(compareBy(titleComparator) { it.title })
    }
    return CatalogResult(visible = visible, ordered = ordered, continueReading = continueReading)
}

/**
 * Pure filter decision, kept free of Android types so it can be unit tested.
 */
internal fun matchesCatalogFilter(
    format: String,
    readProgress: Float,
    isFavorite: Boolean,
    hasOfflineCopy: Boolean,
    collectionNames: List<String>,
    selectedCollection: String?,
    filter: LibraryFilter
): Boolean {
    if (selectedCollection != null && selectedCollection !in collectionNames) return false
    return when (filter) {
        LibraryFilter.TO_READ -> format != "Audio" && readProgress <= 0f
        LibraryFilter.OFFLINE_COPY -> hasOfflineCopy
        LibraryFilter.IN_PROGRESS -> format != "Audio" && readProgress > 0f && readProgress < 1f
        LibraryFilter.COMPLETED -> format != "Audio" && readProgress >= 1f
        LibraryFilter.FAVORITES -> isFavorite
        LibraryFilter.EPUB -> format == "EPUB"
        LibraryFilter.PDF -> format == "PDF"
        LibraryFilter.AUDIO -> format == "Audio"
        LibraryFilter.ALL -> true
    }
}
