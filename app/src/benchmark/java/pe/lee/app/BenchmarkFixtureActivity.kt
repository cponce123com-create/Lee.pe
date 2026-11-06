package pe.lee.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.LeeDatabase
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.ui.reader.PAGE_TURN_STYLE_KEY
import pe.lee.app.ui.reader.PageTurnStyle

/** Test-only seed endpoint: this class and its exported activity are absent from debug/release APKs. */
class BenchmarkFixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Synchronous setup is outside all measured blocks; finish only after durable writes.
        runBlocking(Dispatchers.IO) {
            val directory = File(filesDir, "bundled_books").apply { mkdirs() }
            val books = (0 until 60).map { index ->
                val file = File(directory, "benchmark-$index.epub")
                if (!file.exists()) assets.open("el-tesoro-de-los-incas.epub").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                BookEntity(
                    id = "benchmark-$index",
                    uri = FileProvider.getUriForFile(this@BenchmarkFixtureActivity, "$packageName.bundled", file).toString(),
                    title = "Benchmark %02d".format(index),
                    format = "EPUB",
                    importedAt = index.toLong(),
                )
            }
            intent.getStringExtra("pageTurnStyle")?.let { requested ->
                val style = PageTurnStyle.valueOf(requested)
                check(SettingsRepository(this@BenchmarkFixtureActivity).update(SettingsRepository.Store.LIBRARY) {
                    putString(PAGE_TURN_STYLE_KEY, style.name)
                })
            }
            LeeDatabase.get(this@BenchmarkFixtureActivity).bookDao().insertAll(books)
        }
        setContentView(TextView(this).apply { text = "Benchmark fixture ready" })
    }
}
