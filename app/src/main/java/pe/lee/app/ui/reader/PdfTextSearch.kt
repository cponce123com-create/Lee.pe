package pe.lee.app.ui.reader

import android.content.res.Resources
import android.graphics.pdf.PdfRenderer
import android.os.Build
import androidx.annotation.RequiresApi
import pe.lee.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class PdfSearchOutcome(
    val results: List<PdfSearchResult>,
    val message: String
)

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal suspend fun searchPdfDocument(
    renderer: PdfRenderer,
    pageCount: Int,
    term: String,
    renderMutex: Mutex,
    resources: Resources
): PdfSearchOutcome {
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
                    for (textContent in textContents) {
                        val blockText = textContent.text
                        var matchIndex = blockText.indexOf(term, ignoreCase = true)
                        if (matchIndex < 0) continue

                        val rects = textContent.bounds.mapNotNull { bounds ->
                            if (page.width <= 0 || page.height <= 0) return@mapNotNull null
                            PdfNormalizedRect(
                                left = (bounds.left / page.width).coerceIn(0f, 1f),
                                top = (bounds.top / page.height).coerceIn(0f, 1f),
                                right = (bounds.right / page.width).coerceIn(0f, 1f),
                                bottom = (bounds.bottom / page.height).coerceIn(0f, 1f)
                            )
                        }
                        while (matchIndex >= 0 && matches.size + pageMatches.size < 100) {
                            val snippetStart = (matchIndex - 45).coerceAtLeast(0)
                            val snippetEnd = (matchIndex + term.length + 75).coerceAtMost(blockText.length)
                            val snippet = buildString {
                                if (snippetStart > 0) append('…')
                                append(blockText.substring(snippetStart, snippetEnd).trim())
                                if (snippetEnd < blockText.length) append('…')
                            }
                            pageMatches += PdfSearchResult(searchPageIndex, snippet, rects)
                            matchIndex = blockText.indexOf(
                                term,
                                startIndex = matchIndex + term.length,
                                ignoreCase = true
                            )
                        }
                        if (matches.size + pageMatches.size >= 100) break
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
            matches.size
        )
        !hasSelectableText -> resources.getString(R.string.pdf_search_no_selectable_text)
        else -> resources.getString(R.string.pdf_search_no_matches)
    }
    return PdfSearchOutcome(results, message)
}
