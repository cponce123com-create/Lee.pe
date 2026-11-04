package pe.lee.core.domain.library

import java.text.Normalizer
import java.util.Locale

/** Immutable catalog index. Build once when book fields change, then reuse for each query. */
class SearchBooksUseCase<T>(books: List<T>, searchableText: (T) -> String) {
    private val entries = books.map { it to normalizeLibraryText(searchableText(it)) }

    /** Matches every word, in any order, ignoring case, accents and repeated whitespace. */
    operator fun invoke(query: String): List<T> {
        val words = normalizeLibraryText(query).split(' ').filter(String::isNotEmpty)
        return entries.filter { (_, text) -> words.all(text::contains) }.map { it.first }
    }
}

private val combiningMarks = Regex("\\p{M}+")
private val whitespace = Regex("\\s+")

/** Stable locale-independent normalization shared by catalog indexing and queries. */
fun normalizeLibraryText(input: String): String =
    Normalizer.normalize(input.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .replace(whitespace, " ")
