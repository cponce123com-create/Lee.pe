package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.CollectionEntity
import pe.lee.app.data.local.LocalBackupMatcher
import pe.lee.app.data.local.LocalBackupMergeReport
import pe.lee.app.data.local.LocalBackupSnapshot
import pe.lee.app.data.local.ReadingAnnotationEntity
import pe.lee.app.data.local.ReadingSessionEntity
import pe.lee.app.data.repository.AudioProgressStore
import pe.lee.app.data.repository.LocalBackupRepository
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.core.domain.BackupArchiveException
import pe.lee.core.domain.LocalBackupCipher
import pe.lee.core.domain.LocalTextLimits
import pe.lee.core.domain.library.ExportBackupUseCase
import pe.lee.core.domain.library.ImportBackupUseCase

private const val BACKUP_SCHEMA = "lee.pe.local-library"
private const val BACKUP_VERSION = 2
private const val MIN_BACKUP_VERSION = 1
internal const val MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
private const val MAX_PDF_PAGE_INDEX = 1_000_000
private val DAY_KEY = Regex("\\d{4}-\\d{2}-\\d{2}")
private const val ENCRYPTED_BACKUP_OVERHEAD_BYTES = 57
private const val MAX_BOOKS = 20000
private const val MAX_ANNOTATIONS = 60000
private const val MAX_SESSIONS = 100000
private const val MAX_COLLECTIONS = 2000

internal class BackupPasswordRequiredException : IOException()

internal data class LocalBackupArchive(val snapshot: LocalBackupSnapshot, val settings: Map<String, JSONObject>, val dailyGoalMinutes: Int?)
internal data class LocalLibraryRestoreResult(
    val merge: LocalBackupMergeReport,
    val preferencesPersisted: Boolean,
)

internal data class LocalBackupPreview(val archive: LocalBackupArchive, val exactMatches: Int, val titleMatches: Int, val unavailable: Int)

internal data class SanitizedBackupSnapshot(val snapshot: LocalBackupSnapshot, val adjustedRecords: Int)

internal object LocalLibraryBackup {
    suspend fun save(context: Context, uri: Uri, backupRepository: LocalBackupRepository, password: String) = withContext(Dispatchers.IO) {
        try {
            val sanitized = sanitizeSnapshot(backupRepository.snapshot())
            val snapshot = sanitized.snapshot
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
            val collections = JSONArray()
            val bookSettings = JSONObject()
            // One read of the audio positions instead of one SharedPreferences lookup
            // per audio book (A-11).
            val audioPositions = SettingsRepository(context).all(SettingsRepository.Store.AUDIO_PROGRESS)
            for (b in snapshot.books) {
                val bookJson = JSONObject().put("uri", b.uri).put("title", b.title)
                    .put("format", b.format).put("importedAt", b.importedAt)
                    .put("lastOpenedAt", b.lastOpenedAt)
                    .put("epubLocator", b.epubLocator)
                    .put("readProgress", b.readProgress.toDouble())
                    .put("pdfPageIndex", b.pdfPageIndex)
                    .put("isFavorite", b.isFavorite).put("collectionId", b.collectionId)
                // Version 2: every shelf of the book, not just the legacy single name.
                val collectionNames = snapshot.bookCollectionNames[b.uri].orEmpty()
                if (collectionNames.isNotEmpty()) {
                    bookJson.put("collections", JSONArray(collectionNames))
                }
                books.put(bookJson)
                val extras = JSONObject()
                for (key in listOf(
                    "epub-preferences:",
                    "reader-brightness:",
                    "keep-screen-on:",
                    "tts-speed:",
                    "tts-voice:",
                    "pdf-continuous:",
                )) {
                    val source = key + b.uri
                    if (librarySettings.containsKey(source)) {
                        extras.put(key, librarySettings[source])
                    }
                }
                if (b.format == "Audio") {
                    extras.put(
                        "audioPositionMs",
                        audioPositions[AudioProgressStore.positionKey(b.uri)] ?: 0L,
                    )
                }
                bookSettings.put(b.uri, extras)
            }
            for (collection in snapshot.collections) {
                collections.put(
                    JSONObject()
                        .put("name", collection.name)
                        .put("sortOrder", collection.sortOrder),
                )
            }
            for (a in snapshot.annotations) {
                annotations.put(
                    JSONObject().put("id", a.id).put("bookUri", a.bookUri)
                        .put("kind", a.kind).put("locatorJson", a.locatorJson)
                        .put("selectedText", a.selectedText).put("noteText", a.noteText)
                        .put("colorArgb", a.colorArgb).put("createdAt", a.createdAt)
                        .put("updatedAt", a.updatedAt),
                )
            }
            for (s in snapshot.sessions) {
                sessions.put(
                    JSONObject().put("dayKey", s.dayKey).put("bookUri", s.bookUri)
                        .put("durationSeconds", s.durationSeconds),
                )
            }
            root.put("books", books).put("annotations", annotations)
                .put("sessions", sessions).put("settings", bookSettings)
                .put("collections", collections)
            val bytes = root.toString().toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_ARCHIVE_BYTES - ENCRYPTED_BACKUP_OVERHEAD_BYTES) {
                bytes.fill(0)
                throw BackupArchiveException(BackupArchiveException.Reason.SIZE_LIMIT)
            }
            // Never write an archive that our own importer cannot read.
            parse(bytes.toString(Charsets.UTF_8))
            val encrypted = try {
                ExportBackupUseCase()(bytes, password)
            } finally {
                bytes.fill(0)
            }
            try {
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw BackupArchiveException(BackupArchiveException.Reason.DESTINATION_UNAVAILABLE)
                output.use { it.write(encrypted) }
            } finally {
                encrypted.fill(0)
            }
            sanitized.adjustedRecords
        } catch (error: OutOfMemoryError) {
            throw BackupArchiveException(BackupArchiveException.Reason.SIZE_LIMIT, error)
        }
    }

    suspend fun preview(context: Context, uri: Uri, backupRepository: LocalBackupRepository, password: String? = null): LocalBackupPreview =
        withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw BackupArchiveException(BackupArchiveException.Reason.SOURCE_UNAVAILABLE)
                val payload = readArchivePayload(input)
                val jsonBytes = try {
                    if (LocalBackupCipher.isEncrypted(payload)) {
                        val suppliedPassword = password ?: throw BackupPasswordRequiredException()
                        ImportBackupUseCase()(payload, suppliedPassword)
                    } else {
                        payload
                    }
                } catch (error: Exception) {
                    payload.fill(0)
                    throw error
                }
                val archive = try {
                    parse(jsonBytes.toString(Charsets.UTF_8))
                } finally {
                    jsonBytes.fill(0)
                    if (jsonBytes !== payload) payload.fill(0)
                }
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
            } catch (error: OutOfMemoryError) {
                throw BackupArchiveException(BackupArchiveException.Reason.SIZE_LIMIT, error)
            }
        }

    internal fun isValidBackupDayKey(value: String): Boolean = DAY_KEY.matches(value) && runCatching { LocalDate.parse(value) }.isSuccess

    internal fun readArchivePayload(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream(8 * 1024)
        input.use { stream ->
            val chunk = ByteArray(8 * 1024)
            var totalBytes = 0
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                if (totalBytes + read > MAX_ARCHIVE_BYTES) {
                    throw BackupArchiveException(BackupArchiveException.Reason.SIZE_LIMIT)
                }
                buffer.write(chunk, 0, read)
                totalBytes += read
            }
        }
        return buffer.toByteArray()
    }

    @Suppress("CyclomaticComplexMethod")
    suspend fun restore(context: Context, preview: LocalBackupPreview, backupRepository: LocalBackupRepository): LocalLibraryRestoreResult =
        withContext(Dispatchers.IO) {
            val report = backupRepository.merge(preview.archive.snapshot)
            val settingsRepository = SettingsRepository(context)
            val currentSettings = settingsRepository.all(SettingsRepository.Store.LIBRARY)
            val restoredBooks = backupRepository.allBooks()
            val librarySettingsPersisted = settingsRepository.update(SettingsRepository.Store.LIBRARY) {
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
                }
                // PDF activity uses this legacy key first. Align it to Room's actual
                // post-merge position, not blindly to a possibly older backup.
                for (book in restoredBooks) {
                    if (book.uri in report.uriMap.values && book.format == "PDF") {
                        putInt("pdf-page:${book.uri}", book.pdfPageIndex)
                    }
                }
            }
            val audioPositions = report.uriMap.mapNotNull { (backupUri, localUri) ->
                val settings = preview.archive.settings[backupUri] ?: return@mapNotNull null
                if (!settings.has("audioPositionMs") || AudioProgressStore.get(context, localUri) != 0L) return@mapNotNull null
                AudioProgressStore.positionKey(localUri) to settings.getLong("audioPositionMs").coerceAtLeast(0L)
            }
            val audioSettingsPersisted = if (audioPositions.isEmpty()) {
                true
            } else {
                settingsRepository.update(SettingsRepository.Store.AUDIO_PROGRESS) {
                    audioPositions.forEach { (key, position) -> putLong(key, position) }
                }
            }
            val statsPersisted = preview.archive.dailyGoalMinutes?.let { goal ->
                if (!settingsRepository.contains(SettingsRepository.Store.READING_STATS, "daily_goal_minutes")) {
                    settingsRepository.update(SettingsRepository.Store.READING_STATS) {
                        putInt("daily_goal_minutes", goal.coerceIn(5, 240))
                    }
                } else {
                    true
                }
            } ?: true
            LocalLibraryRestoreResult(
                merge = report,
                preferencesPersisted = librarySettingsPersisted && audioSettingsPersisted && statsPersisted,
            )
        }

    internal fun parse(json: String): LocalBackupArchive {
        try {
            val root = JSONObject(json)
            val version = root.getInt("version")
            if (root.getString("schema") != BACKUP_SCHEMA ||
                version !in MIN_BACKUP_VERSION..BACKUP_VERSION
            ) {
                throw BackupArchiveException(BackupArchiveException.Reason.UNSUPPORTED_FORMAT)
            }
            val rawBooks = root.getJSONArray("books")
            val rawNotes = root.getJSONArray("annotations")
            val rawSessions = root.getJSONArray("sessions")
            if (rawBooks.length() > MAX_BOOKS ||
                rawNotes.length() > MAX_ANNOTATIONS ||
                rawSessions.length() > MAX_SESSIONS
            ) {
                throw BackupArchiveException(BackupArchiveException.Reason.RECORD_LIMIT)
            }

            fun JSONObject.text(name: String, max: Int): String = getString(name).takeIf { it.length in 1..max }
                ?: throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
            fun JSONObject.maybeText(name: String, max: Int): String? = if (!has(name) || isNull(name)) {
                null
            } else {
                getString(name)
                    .also { if (it.length > max) throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA) }
            }

            val books = ArrayList<BookEntity>()
            val uniqueUris = HashSet<String>()
            val collectionNamesByBook = HashMap<String, List<String>>()
            for (i in 0 until rawBooks.length()) {
                val o = rawBooks.getJSONObject(i)
                val bookUri = o.text("uri", 4096)
                val format = o.text("format", 12)
                if (format !in setOf("EPUB", "PDF", "Audio") || !uniqueUris.add(bookUri)) {
                    throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                }
                val progression = o.getDouble("readProgress")
                if (!progression.isFinite() || progression !in 0.0..1.0) {
                    throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                }
                val page = o.getInt("pdfPageIndex")
                if (page !in 0..MAX_PDF_PAGE_INDEX) throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                val savedCollections = o.optJSONArray("collections")?.let { array ->
                    buildList {
                        if (array.length() > MAX_COLLECTIONS) {
                            throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                        }
                        for (index in 0 until array.length()) {
                            val name = array.getString(index).trim()
                            if (name.isEmpty() || name.length > LocalTextLimits.COLLECTION_NAME) {
                                throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                            }
                            add(name)
                        }
                    }.distinctBy { it.lowercase(Locale.ROOT) }
                }.orEmpty()
                if (savedCollections.isNotEmpty()) {
                    collectionNamesByBook[bookUri] = savedCollections
                }
                books.add(
                    BookEntity(
                        uri = bookUri, title = o.text("title", LocalTextLimits.BOOK_TITLE), format = format,
                        importedAt = o.getLong("importedAt").coerceAtLeast(0L),
                        lastOpenedAt = if (o.isNull("lastOpenedAt")) null else o.getLong("lastOpenedAt").coerceAtLeast(0L),
                        epubLocator = o.maybeText("epubLocator", LocalTextLimits.LOCATOR_JSON),
                        readProgress = progression.toFloat(), pdfPageIndex = page,
                        isFavorite = o.getBoolean("isFavorite"),
                        collectionId = o.maybeText("collectionId", LocalTextLimits.COLLECTION_NAME),
                    ),
                )
            }
            val restoredCollections = ArrayList<CollectionEntity>()
            root.optJSONArray("collections")?.let { array ->
                if (array.length() > MAX_COLLECTIONS) {
                    throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                }
                val seenNames = HashSet<String>()
                for (index in 0 until array.length()) {
                    val o = array.getJSONObject(index)
                    val name = o.text("name", LocalTextLimits.COLLECTION_NAME).trim()
                    val normalized = name.lowercase(Locale.ROOT)
                    if (normalized.isEmpty() || !seenNames.add(normalized)) continue
                    restoredCollections.add(
                        CollectionEntity(
                            name = name,
                            normalizedName = normalized,
                            sortOrder = o.optInt("sortOrder", 0),
                        ),
                    )
                }
            }
            val validUris = uniqueUris
            val notes = ArrayList<ReadingAnnotationEntity>()
            val noteIds = HashSet<String>()
            for (i in 0 until rawNotes.length()) {
                val o = rawNotes.getJSONObject(i)
                val id = o.text("id", 128)
                val bookUri = o.text("bookUri", 4096)
                val kind = o.text("kind", 24)
                if (!noteIds.add(id) ||
                    bookUri !in validUris ||
                    kind !in setOf("NOTE", "HIGHLIGHT", "BOOKMARK")
                ) {
                    throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                }
                notes.add(
                    ReadingAnnotationEntity(
                        id = id, bookUri = bookUri, kind = kind,
                        locatorJson = o.text("locatorJson", LocalTextLimits.LOCATOR_JSON),
                        selectedText = o.maybeText("selectedText", LocalTextLimits.ANNOTATION_TEXT),
                        noteText = o.maybeText("noteText", LocalTextLimits.ANNOTATION_TEXT),
                        colorArgb = if (o.isNull("colorArgb")) null else o.getInt("colorArgb"),
                        createdAt = o.getLong("createdAt").coerceAtLeast(0L),
                        updatedAt = o.getLong("updatedAt").coerceAtLeast(0L),
                    ),
                )
            }
            val sessions = ArrayList<ReadingSessionEntity>()
            for (i in 0 until rawSessions.length()) {
                val o = rawSessions.getJSONObject(i)
                val uri = o.text("bookUri", 4096)
                val day = o.text("dayKey", 10)
                val amount = o.getLong("durationSeconds")
                if (uri !in validUris ||
                    !isValidBackupDayKey(day) ||
                    amount !in 0L..86_400L
                ) {
                    throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                }
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
                        throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                    }
                }
                fun validFloat(key: String, low: Double, high: Double) {
                    if (!o.has(key) || o.isNull(key)) return
                    val value = o.get(key)
                    if (value !is Number ||
                        !value.toDouble().isFinite() ||
                        value.toDouble() !in low..high
                    ) {
                        throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                    }
                }
                fun validBoolean(key: String) {
                    if (o.has(key) && !o.isNull(key) && o.get(key) !is Boolean) {
                        throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
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
                        throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA)
                    }
                }
                extras[uri] = o
            }
            val goal = if (root.has("dailyGoalMinutes") && !root.isNull("dailyGoalMinutes")) {
                root.getInt("dailyGoalMinutes").coerceIn(5, 240)
            } else {
                null
            }
            return LocalBackupArchive(
                LocalBackupSnapshot(
                    books = books,
                    annotations = notes,
                    sessions = sessions,
                    collections = restoredCollections,
                    bookCollectionNames = collectionNamesByBook,
                ),
                extras,
                goal,
            )
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw BackupArchiveException(BackupArchiveException.Reason.INVALID_DATA, error)
        }
    }

    internal fun sanitizeSnapshot(snapshot: LocalBackupSnapshot): SanitizedBackupSnapshot {
        var adjusted = 0
        val books = snapshot.books.take(MAX_BOOKS).map { book ->
            val title = book.title.take(LocalTextLimits.BOOK_TITLE)
            val collection = book.collectionId?.take(LocalTextLimits.COLLECTION_NAME)
            val locator = book.epubLocator?.takeIf { it.length <= LocalTextLimits.LOCATOR_JSON }
            if (title != book.title || collection != book.collectionId || locator != book.epubLocator) adjusted++
            book.copy(title = title, collectionId = collection, epubLocator = locator)
        }.also { if (snapshot.books.size > MAX_BOOKS) adjusted += snapshot.books.size - MAX_BOOKS }
        val notes = snapshot.annotations.asSequence().mapNotNull { note ->
            if (note.locatorJson.length > LocalTextLimits.LOCATOR_JSON) {
                adjusted++
                return@mapNotNull null
            }
            val selectedText = note.selectedText?.take(LocalTextLimits.ANNOTATION_TEXT)
            val noteText = note.noteText?.take(LocalTextLimits.ANNOTATION_TEXT)
            if (selectedText != note.selectedText || noteText != note.noteText) adjusted++
            note.copy(selectedText = selectedText, noteText = noteText)
        }.take(MAX_ANNOTATIONS).toList().also {
            if (snapshot.annotations.size > MAX_ANNOTATIONS) adjusted += snapshot.annotations.size - MAX_ANNOTATIONS
        }
        val collections = snapshot.collections.take(MAX_COLLECTIONS).map { collection ->
            val name = collection.name.take(LocalTextLimits.COLLECTION_NAME)
            if (name != collection.name) adjusted++
            collection.copy(name = name, normalizedName = name.lowercase(Locale.ROOT))
        }.also { if (snapshot.collections.size > MAX_COLLECTIONS) adjusted += snapshot.collections.size - MAX_COLLECTIONS }
        val bookCollectionNames = snapshot.bookCollectionNames.mapValues { (_, names) ->
            names.mapNotNull { name ->
                if (name.length > LocalTextLimits.COLLECTION_NAME) adjusted++
                name.take(LocalTextLimits.COLLECTION_NAME).takeIf(String::isNotBlank)
            }.distinctBy { it.lowercase(Locale.ROOT) }.take(MAX_COLLECTIONS)
        }
        val sessions = snapshot.sessions.take(MAX_SESSIONS).also {
            if (snapshot.sessions.size > MAX_SESSIONS) adjusted += snapshot.sessions.size - MAX_SESSIONS
        }
        return SanitizedBackupSnapshot(
            snapshot.copy(
                books = books,
                annotations = notes,
                sessions = sessions,
                collections = collections,
                bookCollectionNames = bookCollectionNames,
            ),
            adjusted,
        )
    }
}
