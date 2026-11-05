package pe.lee.app.ui.reader

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An exact-match result from the bundled Spanish-English dictionary. */
data class OfflineDictionaryEntry(
    val headword: String,
    val definitions: List<String>,
)

/**
 * Local exact-match lookup. The bundled asset is expanded once into app-private storage;
 * each lookup then binary-searches the sorted TSV on disk. All file work runs on Dispatchers.IO.
 */
internal class OfflineDictionaryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, "offline-dictionary-es-en-v1.tsv")

    suspend fun lookup(term: String): OfflineDictionaryEntry? = withContext(Dispatchers.IO) {
        val normalized = normalizeDictionaryTerm(term)
        if (normalized.isEmpty() || normalized.length > MAX_TERM_LENGTH) return@withContext null
        val file = ensureCache()
        RandomAccessFile(file, "r").use { data ->
            val row = binarySearchRows(
                byteLength = data.length(),
                rowAt = { offset -> readRowAt(data, offset) },
                keyOf = { it.substringBefore('\t') },
                compare = { it.compareTo(normalized) },
            ) ?: return@use null
            val fields = row.split('\t', limit = 3)
            if (fields.size != 3) return@use null
            val definitions = fields[2].split(UNIT_SEPARATOR).filter(String::isNotBlank).take(MAX_DEFINITIONS)
            if (definitions.isEmpty()) null else OfflineDictionaryEntry(fields[1], definitions)
        }
    }

    private fun ensureCache(): File = synchronized(CACHE_LOCK) {
        if (cacheFile.isFile && cacheFile.length() > 0L) return@synchronized cacheFile
        val parent = cacheFile.parentFile ?: error("Dictionary cache directory unavailable")
        check(parent.exists() || parent.mkdirs()) { "Could not create dictionary cache directory" }
        val temporary = File(parent, "${cacheFile.name}.tmp")
        try {
            appContext.assets.open(ASSET_PATH).use { asset ->
                GZIPInputStream(asset).use { gzip ->
                    temporary.outputStream().buffered().use { output -> gzip.copyTo(output) }
                }
            }
            check(temporary.length() > 0L) { "Bundled dictionary is empty" }
            if (cacheFile.exists() && !cacheFile.delete()) error("Could not replace dictionary cache")
            check(temporary.renameTo(cacheFile)) { "Could not install dictionary cache" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        cacheFile
    }

    /** Finds the containing line for an arbitrary byte offset without decoding partial UTF-8. */
    private fun readRowAt(file: RandomAccessFile, requestedOffset: Long): String? {
        if (requestedOffset < 0L || requestedOffset >= file.length()) return null
        var start = requestedOffset
        if (start > 0L) {
            file.seek(start - 1L)
            if (file.read() != '\n'.code) {
                file.seek(start)
                var found = false
                while (file.read() != -1) {
                    val afterByte = file.filePointer
                    file.seek(afterByte - 1L)
                    val isNewline = file.read() == '\n'.code
                    file.seek(afterByte)
                    if (isNewline) {
                        start = afterByte
                        found = true
                        break
                    }
                }
                if (!found) return null
            }
        }
        file.seek(start)
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = file.read()
            if (value < 0 || value == '\n'.code) break
            bytes.write(value)
        }
        return bytes.toByteArray().toString(Charsets.UTF_8).trimEnd('\r').takeIf(String::isNotEmpty)
    }

    private companion object {
        const val ASSET_PATH = "dictionary/es-en.tsv.gz"
        const val UNIT_SEPARATOR = '\u001f'
        const val MAX_TERM_LENGTH = 160
        const val MAX_DEFINITIONS = 8
        val CACHE_LOCK = Any()
    }
}

/** Locale-independent lowercase key with combining diacritics removed. */
internal fun normalizeDictionaryTerm(term: String): String = buildString(term.length) {
    val decomposed = Normalizer.normalize(term.trim(), Normalizer.Form.NFD)
    decomposed.codePoints().forEach { codePoint ->
        val type = Character.getType(codePoint)
        if (type != Character.NON_SPACING_MARK.toInt() &&
            type != Character.COMBINING_SPACING_MARK.toInt() &&
            type != Character.ENCLOSING_MARK.toInt()
        ) append(String(Character.toChars(codePoint)).lowercase(Locale.ROOT))
    }
}

/** Generic binary search over a sorted row source, used by the file lookup and pure tests. */
private inline fun binarySearchRows(
    byteLength: Long,
    rowAt: (Long) -> String?,
    keyOf: (String) -> String,
    compare: (String) -> Int,
): String? {
    var low = 0L
    var high = byteLength - 1L
    while (low <= high) {
        val row = rowAt((low + high) ushr 1) ?: return null
        val middle = (low + high) ushr 1
        when {
            compare(keyOf(row)) < 0 -> low = middle + 1L
            compare(keyOf(row)) > 0 -> high = middle - 1L
            else -> return row
        }
    }
    return null
}

/** Pure exact-match helper for unit tests of normalized key and not-found semantics. */
internal fun findDictionaryRow(rows: List<String>, term: String): String? {
    val key = normalizeDictionaryTerm(term)
    if (key.isEmpty()) return null
    return binarySearchRows(
        byteLength = rows.size.toLong(),
        rowAt = { rows.getOrNull(it.toInt()) },
        keyOf = { it.substringBefore('\t') },
        compare = { it.compareTo(key) },
    )
}
