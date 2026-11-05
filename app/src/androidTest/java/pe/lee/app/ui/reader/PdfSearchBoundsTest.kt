package pe.lee.app.ui.reader

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@RequiresApi(35)
@SdkSuppress(minSdkVersion = 35)
class PdfSearchBoundsTest {
    @Test
    fun repeatedWordOnOneLineHasDistinctBounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("search-bounds", ".pdf", context.cacheDir)
        try {
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(400, 200, 1).create())
                val paint = Paint().apply { textSize = 20f }
                page.canvas.drawText("luna y luna", 20f, 50f, paint)
                document.finishPage(page)
                file.outputStream().use(document::writeTo)
            } finally {
                document.close()
            }
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                val results = runBlocking {
                    searchPdfDocument(renderer, 1, "luna", Mutex(), context.resources).results
                }
                assertEquals(2, results.size)
                val bounds = results.map { it.rects.single() }.sortedBy { it.left }
                assertTrue(bounds[0].right < bounds[1].left)
            }
        } finally {
            file.delete()
        }
    }
}
