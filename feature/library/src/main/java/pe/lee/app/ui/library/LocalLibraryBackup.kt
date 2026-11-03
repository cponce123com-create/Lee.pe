package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.LocalBackupMergeReport
import pe.lee.app.data.local.LocalBackupSnapshot
import pe.lee.app.data.local.ReadingAnnotationEntity
import pe.lee.app.data.local.ReadingSessionEntity
import pe.lee.app.data.local.LocalBackupMatcher
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.data.repository.AudioProgressStore
import pe.lee.app.data.repository.LocalBackupRepository
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val BACKUP_SCHEMA = "lee.pe.local-library"
private const val BACKUP_VERSION = 1
private const val MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
private const val MAX_BOOKS = 20000
private const val MAX_ANNOTATIONS = 60000
private const val MAX_SESSIONS = 100000

internal data class LocalBackupArchive(
    val snapshot: LocalBackupSnapshot,
    val settings: Map<String, JSONObject>,
    val dailyGoalMinutes: Int?
)

internal data class LocalBackupPreview(
    val archive: LocalBackupArchive,
    val exactMatches: Int,
    val titleMatches: Int,
    val unavailable: Int
)

internal object LocalLibraryBackup {
    suspend fun save(context: Context, uri: Uri, backupRepository: LocalBackupRepository) = withContext(Dispatchers.IO) {
        val snapshot = backupRepository.snapshot()
        val settingsRepository = SettingsRepository(context)
        val librarySettings = settingsRepository.all(SettingsRepository.Store.LIBRARY)
        val goal = settingsRepository.getInt(SettingsRepository.Store.READING_STATS, "daily_goal_minutes", 30)
        val root = JSONObject()
            .put("schema", BACKUP_SCHEMA)
            .put("version", BACKUP_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("dailyGoalMinutes", goal)
        val books = JSONArray()
        val annotations = JSONArray()
        val sessions = JSONArray()
        val bookSettings = JSONObject()
        for (b in snapshot.books) {
            books.put(JSONObject().put("uri", b.uri).put("title", b.title)
                .put("format", b.format).put("importedAt", b.importedAt)
                .put("lastOpenedAt", b.lastOpenedAt)
                .put("epubLocator", b.epubLocator)
                .put("readProgress", b.readProgress.toDouble())
                .put("pdfPageIndex", b.pdfPageIndex)
                .put("isFavorite", b.isFavorite).put("collectionId", b.collectionId))
            val extras = JSONObject()
            for (key in listOf("epub-preferences:", "reader-brightness:", "keep-screen-on:",
                "tts-speed:", "tts-voice:", "pdf-continuous:")) {
                val source = key + b.uri
                if (settingsRepository.contains(SettingsRepository.Store.LIBRARY, source)) {
                    extras.put(key, librarySettings[source])
                }
            }
            if (b.format == "Audio") {
                extras.put("audioPositionMs", AudioProgressStore.get(context, b.uri))
            }
            bookSettings.put(b.uri, extras)
        }
        for (a in snapshot.annotations) {
            annotations.put(JSONObject().put("id", a.id).put("bookUri", a.bookUri)
                .put("kind", a.kind).put("locatorJson", a.locatorJson)
                .put("selectedText", a.selectedText).put("noteText", a.noteText)
                .put("colorArgb", a.colorArgb).put("createdAt", a.createdAt)
                .put("updatedAt", a.updatedAt))
        }
        for (s in snapshot.sessions) {
            sessions.put(JSONObject().put("dayKey", s.dayKey).put("bookUri", s.bookUri)
                .put("durationSeconds", s.durationSeconds))
        }
        root.put("books", books).put("annotations", annotations)
            .put("sessions", sessions).put("settings", bookSettings)
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_ARCHIVE_BYTES) {
            throw IOException("El respaldo supera 16 MB. Exporta las anotaciones por libro.")
        }
        // Never write an archive that our own importer cannot read.
        parse(bytes.toString(Charsets.UTF_8))
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("No se pudo abrir el destino del respaldo.")
        output.use { it.write(bytes) }
    }

    suspend fun preview(
        context: Context,
        uri: Uri,
        backupRepository: LocalBackupRepository
    ): LocalBackupPreview = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir el respaldo.")
        val buffer = ByteArrayOutputStream()
        input.use { stream ->
            val chunk = ByteArray(8192)
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                if (buffer.size() + n > MAX_ARCHIVE_BYTES) throw IOException("Respaldo demasiado grande (máximo 16 MB).")
                buffer.write(chunk, 0, n)
            }
        }
        val archive = parse(buffer.toString(Charsets.UTF_8.name()))
        val current = backupRepository.allBooks()
        val matcher = LocalBackupMatcher(current)
        val used = HashSet<String>()
        var exact = 0
        var byTitle = 0
        archive.snapshot.books.forEach { book ->
            val match = matcher.find(book)
            if (match != null && used.add(match.uri)) {
                if (match.uri == book.uri) exact++ else byTitle++
            }
        }
        LocalBackupPreview(archive, exact, byTitle, archive.snapshot.books.size - exact - byTitle)
    }

    suspend fun restore(
        context: Context,
        preview: LocalBackupPreview,
        backupRepository: LocalBackupRepository
    ): LocalBackupMergeReport =
        withContext(Dispatchers.IO) {
            val report = backupRepository.merge(preview.archive.snapshot)
            val settingsRepository = SettingsRepository(context)
            val currentSettings = settingsRepository.all(SettingsRepository.Store.LIBRARY)
            val restoredBooks = backupRepository.allBooks()
            settingsRepository.apply(SettingsRepository.Store.LIBRARY) {
            for ((backupUri, localUri) in report.uriMap) {
                val settings = preview.archive.settings[backupUri] ?: continue
                fun setIfAbsent(key: String, write: () -> Unit) {
                    if (key + localUri !in currentSettings && settings.has(key) && !settings.isNull(key)) {
                        write()
                    }
                }
                setIfAbsent("epub-preferences:") {
                    putString("epub-preferences:$localUri", settings.getString("epub-preferences:"))
                }
                setIfAbsent("reader-brightness:") {
                    putFloat("reader-brightness:$localUri", settings.getDouble("reader-brightness:").toFloat().coerceIn(-1f, 1f))
                }
                setIfAbsent("keep-screen-on:") {
                    putBoolean("keep-screen-on:$localUri", settings.getBoolean("keep-screen-on:"))
                }
                setIfAbsent("tts-speed:") {
                    putFloat("tts-speed:$localUri", settings.getDouble("tts-speed:").toFloat().coerceIn(0.5f, 2f))
                }
                setIfAbsent("tts-voice:") {
                    putString("tts-voice:$localUri", settings.getString("tts-voice:"))
                }
                setIfAbsent("pdf-continuous:") {
                    putBoolean("pdf-continuous:$localUri", settings.getBoolean("pdf-continuous:"))
                }
                if (settings.has("audioPositionMs") && AudioProgressStore.get(context, localUri) == 0L) {
                    AudioProgressStore.put(context, localUri, settings.getLong("audioPositionMs").coerceAtLeast(0L))
                }
            }
            // PDF activity uses this legacy key first. Align it to Room's actual
            // post-merge position, not blindly to a possibly older backup.
            for (book in restoredBooks) {
                if (book.uri in report.uriMap.values && book.format == "PDF") {
                    putInt("pdf-page:${book.uri}", book.pdfPageIndex)
                }
            }
            }
            preview.archive.dailyGoalMinutes?.let { goal ->
                if (!settingsRepository.contains(SettingsRepository.Store.READING_STATS, "daily_goal_minutes")) {
                    settingsRepository.apply(SettingsRepository.Store.READING_STATS) {
                        putInt("daily_goal_minutes", goal.coerceIn(5, 240))
                    }
                }
            }
            report
        }

    internal fun parse(json: String): LocalBackupArchive {
        try {
            val root = JSONObject(json)
            if (root.getString("schema") != BACKUP_SCHEMA || root.getInt("version") != BACKUP_VERSION) {
                throw IOException("Este archivo no es un respaldo Lee.pe compatible (versión 1).")
            }
            val rawBooks = root.getJSONArray("books")
            val rawNotes = root.getJSONArray("annotations")
            val rawSessions = root.getJSONArray("sessions")
            if (rawBooks.length() > MAX_BOOKS || rawNotes.length() > MAX_ANNOTATIONS ||
                rawSessions.length() > MAX_SESSIONS) throw IOException("El respaldo contiene demasiados registros.")

            fun JSONObject.text(name: String, max: Int): String =
                getString(name).takeIf { it.length in 1..max }
                    ?: throw IOException("Campo $name incorrecto.")
            fun JSONObject.maybeText(name: String, max: Int): String? =
                if (!has(name) || isNull(name)) null else getString(name)
                    .also { if (it.length > max) throw IOException("Campo $name demasiado largo.") }

            val books = ArrayList<BookEntity>()
            val uniqueUris = HashSet<String>()
            for (i in 0 until rawBooks.length()) {
                val o = rawBooks.getJSONObject(i)
                val bookUri = o.text("uri", 4096)
                val format = o.text("format", 12)
                if (format !in setOf("EPUB", "PDF", "Audio") || !uniqueUris.add(bookUri)) {
                    throw IOException("Libro o formato duplicado o incorrecto en el respaldo.")
                }
                val progression = o.getDouble("readProgress")
                if (!progression.isFinite() || progression !in 0.0..1.0) {
                    throw IOException("Progreso de lectura no válido.")
                }
                val page = o.getInt("pdfPageIndex")
                if (page < 0) throw IOException("Página PDF no válida.")
                books.add(BookEntity(
                    uri = bookUri, title = o.text("title", 500), format = format,
                    importedAt = o.getLong("importedAt").coerceAtLeast(0L),
                    lastOpenedAt = if (o.isNull("lastOpenedAt")) null else o.getLong("lastOpenedAt").coerceAtLeast(0L),
                    epubLocator = o.maybeText("epubLocator", 16000),
                    readProgress = progression.toFloat(), pdfPageIndex = page,
                    isFavorite = o.getBoolean("isFavorite"),
                    collectionId = o.maybeText("collectionId", 120)
                ))
            }
            val validUris = uniqueUris
            val notes = ArrayList<ReadingAnnotationEntity>()
            val noteIds = HashSet<String>()
            for (i in 0 until rawNotes.length()) {
                val o = rawNotes.getJSONObject(i)
                val id = o.text("id", 128)
                val bookUri = o.text("bookUri", 4096)
                val kind = o.text("kind", 24)
                if (!noteIds.add(id) || bookUri !in validUris ||
                    kind !in setOf("NOTE", "HIGHLIGHT", "BOOKMARK")) {
                    throw IOException("Anotación inválida o repetida.")
                }
                notes.add(ReadingAnnotationEntity(
                    id = id, bookUri = bookUri, kind = kind,
                    locatorJson = o.text("locatorJson", 16000),
                    selectedText = o.maybeText("selectedText", 12000),
                    noteText = o.maybeText("noteText", 12000),
                    colorArgb = if (o.isNull("colorArgb")) null else o.getInt("colorArgb"),
                    createdAt = o.getLong("createdAt").coerceAtLeast(0L),
                    updatedAt = o.getLong("updatedAt").coerceAtLeast(0L)
                ))
            }
            val sessions = ArrayList<ReadingSessionEntity>()
            for (i in 0 until rawSessions.length()) {
                val o = rawSessions.getJSONObject(i)
                val uri = o.text("bookUri", 4096)
                val day = o.text("dayKey", 10)
                val amount = o.getLong("durationSeconds")
                if (uri !in validUris || !day.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) ||
                    amount !in 0L..86_400L) throw IOException("Sesión de lectura inválida.")
                sessions.add(ReadingSessionEntity(day, uri, amount))
            }
            val extras = HashMap<String, JSONObject>()
            val settings = root.optJSONObject("settings")
            for (uri in uniqueUris) {
                val o = settings?.optJSONObject(uri) ?: continue
                fun validText(key: String, maxLength: Int) {
                    if (!o.has(key) || o.isNull(key)) return
                    val value = o.get(key)
                    if (value !is String || value.length > maxLength) {
                        throw IOException("Preferencia $key no válida.")
                    }
                }
                fun validFloat(key: String, low: Double, high: Double) {
                    if (!o.has(key) || o.isNull(key)) return
                    val value = o.get(key)
                    if (value !is Number || !value.toDouble().isFinite() ||
                        value.toDouble() !in low..high) {
                        throw IOException("Preferencia $key no válida.")
                    }
                }
                fun validBoolean(key: String) {
                    if (o.has(key) && !o.isNull(key) && o.get(key) !is Boolean) {
                        throw IOException("Preferencia $key no válida.")
                    }
                }
                validText("epub-preferences:", 32000)
                validText("tts-voice:", 512)
                validFloat("reader-brightness:", -1.0, 1.0)
                validFloat("tts-speed:", 0.5, 2.0)
                validBoolean("keep-screen-on:")
                validBoolean("pdf-continuous:")
                if (o.has("audioPositionMs") && !o.isNull("audioPositionMs")) {
                    val value = o.get("audioPositionMs")
                    if (value !is Number || value.toLong() !in 0L..315_360_000_000L) {
                        throw IOException("Posición de audio no válida.")
                    }
                }
                extras[uri] = o
            }
            val goal = if (root.has("dailyGoalMinutes") && !root.isNull("dailyGoalMinutes")) {
                root.getInt("dailyGoalMinutes").coerceIn(5, 240)
            } else null
            return LocalBackupArchive(LocalBackupSnapshot(books, notes, sessions), extras, goal)
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("El respaldo no tiene el formato esperado o está dañado.", error)
        }
    }
}
