package pe.lee.app.ui.reader

import android.content.res.Resources
import android.graphics.pdf.PdfRenderer
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pe.lee.app.R

internal data class PdfSearchOutcome(val results: List<PdfSearchResult>, val message: String)

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal suspend fun searchPdfDocument(
    renderer: PdfRenderer,
    pageCount: Int,
    term: String,
    renderMutex: Mutex,
    resources: Resources,
): PdfSearchOutcome {
    if (term.isBlank()) return PdfSearchOutcome(emptyList(), resources.getString(R.string.pdf_search_no_matches))
    val matches = mutableListOf<PdfSearchResult>()
    var hasSelectableText = false
    for (searchPageIndex in 0 until pageCount) {
        coroutineContext.ensureActive()
        val pageResult = renderMutex.withLock {
            withContext(Dispatchers.IO) {
                val page = renderer.openPage(searchPageIndex)
                try {
                    val textContents = page.getTextContents()
                    val pageMatches = mutableListOf<PdfSearchResult>()
                    val pageHasText = textContents.any { it.text.isNotBlank() }
                    val pageText = textContents.joinToString(separator = "") { it.text }
                    for (match in page.searchText(term).take(100 - matches.size)) {
                        val rects = match.bounds.mapNotNull { bounds ->
                            if (page.width <= 0 || page.height <= 0) return@mapNotNull null
                            PdfNormalizedRect(
                                left = (bounds.left / page.width).coerceIn(0f, 1f),
                                top = (bounds.top / page.height).coerceIn(0f, 1f),
                                right = (bounds.right / page.width).coerceIn(0f, 1f),
                                bottom = (bounds.bottom / page.height).coerceIn(0f, 1f),
                            )
                        }
                        val index = match.textStartIndex
                        val snippet = if (index in pageText.indices) {
                            val start = (index - 45).coerceAtLeast(0)
                            val end = (index + term.length + 75).coerceAtMost(pageText.length)
                            buildString {
                                if (start > 0) append('…')
                                append(pageText.substring(start, end).trim())
                                if (end < pageText.length) append('…')
                            }
                        } else {
                            term
                        }
                        pageMatches += PdfSearchResult(searchPageIndex, snippet, rects)
                    }
                    pageHasText to pageMatches
                } finally {
                    page.close()
                }
            }
        }
        if (pageResult.first) hasSelectableText = true
        matches += pageResult.second
        if (matches.size >= 100) break
    }
    val results = matches.take(100)
    val message = when {
        matches.isNotEmpty() && matches.size >= 100 ->
            resources.getString(R.string.pdf_search_limit_shown)
        matches.isNotEmpty() -> resources.getQuantityString(
            R.plurals.pdf_search_matches_count,
            matches.size,
            matches.size,
        )
        !hasSelectableText -> resources.getString(R.string.pdf_search_no_selectable_text)
        else -> resources.getString(R.string.pdf_search_no_matches)
    }
    return PdfSearchOutcome(results, message)
}
