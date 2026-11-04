package pe.lee.app.ui.library

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scale check for the catalog pipeline (P-06). It measures the CPU cost of the
 * two operations that used to run on the main thread for every Room emission:
 * building the normalized search index and sorting with a Spanish collator.
 *
 * The bounds are deliberately generous so a slow CI machine does not fail the
 * build; the printed numbers are the interesting part and are recorded in
 * docs/METRICAS.md.
 */
class LibraryScaleTest {
    private val bookCount = 10_000

    private fun titles(): List<String> = (1..bookCount).map { index ->
        "Novela número $index de García Márquez — Ñandú y compañía"
    }

    @Test
    fun indexingTenThousandTitlesStaysWithinBudget() {
        val books = titles()
        val startedAt = System.nanoTime()
        val index = books.map { normalizedLibraryText(it) }
        val indexMs = (System.nanoTime() - startedAt) / 1_000_000

        val query = normalizedLibraryText("garcia 9999")
        val filterStart = System.nanoTime()
        val matches = index.count { matchesNormalizedLibraryQuery(it, query) }
        val filterMs = (System.nanoTime() - filterStart) / 1_000_000

        println("P-06 index build: ${indexMs}ms for $bookCount books, filter: ${filterMs}ms, matches=$matches")
        assertEquals(1, matches)
        assertTrue("index build demasiado lento: ${indexMs}ms", indexMs < 3_000)
        assertTrue("filtro demasiado lento: ${filterMs}ms", filterMs < 1_000)
    }

    @Test
    fun sortingTenThousandTitlesWithASpanishCollatorStaysWithinBudget() {
        val comparator = libraryTitleComparator(Locale.forLanguageTag("es-PE"))
        val books = titles()
        val startedAt = System.nanoTime()
        val sorted = books.sortedWith(comparator)
        val sortMs = (System.nanoTime() - startedAt) / 1_000_000

        println("P-06 spanish sort: ${sortMs}ms for $bookCount books")
        assertEquals(bookCount, sorted.size)
        assertEquals(sorted.first(), books.minWith(comparator))
        assertTrue("orden demasiado lento: ${sortMs}ms", sortMs < 3_000)
    }

    @Test
    fun theIndexCostGrowsLinearlyWithTheCatalog() {
        // Warm up once and take the best of two runs: the first pass pays JIT
        // costs and made this assertion flaky (1k cold was faster than 10k warm).
        fun timeFor(count: Int): Long {
            val sample = (1..count).map { "Libro $it de la biblioteca" }
            var best = Long.MAX_VALUE
            repeat(2) {
                val start = System.nanoTime()
                sample.forEach { normalizedLibraryText(it) }
                best = minOf(best, (System.nanoTime() - start) / 1_000_000)
            }
            return best
        }

        timeFor(1_000)
        val small = timeFor(1_000)
        val large = timeFor(10_000)
        println("P-06 scaling: 1k=${small}ms 10k=${large}ms")
        // 10x the books under 40x the time is still linear enough with JIT noise,
        // and catches any accidental quadratic behaviour.
        assertTrue(
            "crecimiento no lineal: 1k=${small}ms 10k=${large}ms",
            large <= small * 40 + 250,
        )
    }
}
