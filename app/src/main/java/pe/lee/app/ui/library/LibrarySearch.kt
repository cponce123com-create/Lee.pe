package pe.lee.app.ui.library

import java.text.Normalizer
import java.util.Locale

/**
 * Local-only catalog search. Normalize accents and whitespace so users can
 * find "García" by typing "garcia" without making a network request.
 */
internal fun normalizedLibraryText(input: String): String =
    Normalizer.normalize(input.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")

/**
 * A query may contain several words in any order and can match a title,
 * file format or collection name. Empty queries always match.
 */
internal fun matchesLibraryQuery(
    title: String,
    format: String,
    collection: String?,
    normalizedQuery: String
): Boolean {
    val fields = normalizedLibraryText("$title $format ${collection.orEmpty()}")
    return matchesNormalizedLibraryQuery(fields, normalizedQuery)
}

/** Search an indexed, already normalized catalog entry with minimal allocations. */
internal fun matchesNormalizedLibraryQuery(
    indexedText: String,
    normalizedQuery: String
): Boolean =
    normalizedQuery.isBlank() ||
        normalizedQuery.split(' ').all { it.isEmpty() || indexedText.contains(it) }
