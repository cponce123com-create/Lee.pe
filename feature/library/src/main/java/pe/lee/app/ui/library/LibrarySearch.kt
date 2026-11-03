package pe.lee.app.ui.library

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Local-only catalog search. Normalize accents and whitespace so users can
 * find "García" by typing "garcia" without making a network request.
 */
private val COMBINING_MARKS = Regex("\\p{M}+")
private val WHITESPACE = Regex("\\s+")

internal fun normalizedLibraryText(input: String): String =
    Normalizer.normalize(input.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(WHITESPACE, " ")

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

/**
 * Alphabetical order for a Spanish catalog: "Ángel" sorts with the A's and
 * "Ñandú" right after the N's, instead of at the end of the list as raw
 * UTF-16 comparison does ("Á" is 0xC1, above "z").
 *
 * Case is ignored (SECONDARY strength) but accents still break ties, so the
 * library reads like a printed index without mixing uppercase and lowercase.
 */
internal fun libraryTitleComparator(locale: Locale = Locale.getDefault()): Comparator<String> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return Comparator { left, right -> collator.compare(left, right) }
}
