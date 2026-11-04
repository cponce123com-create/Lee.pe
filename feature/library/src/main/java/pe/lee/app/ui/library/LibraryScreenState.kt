package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.CollectionEntity
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.data.repository.LocalBackupRepository
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.feature.library.R

private const val DEFAULT_DAILY_GOAL_MINUTES = 30
private const val MIN_DAILY_GOAL_MINUTES = 5
private const val MAX_DAILY_GOAL_MINUTES = 240

/** Composition-owned library state. Saved UI choices keep their existing rememberSaveable lifecycle. */
internal class LibraryScreenState(
    val context: Context,
    val viewModel: LibraryViewModel,
    val settings: SettingsRepository,
    val backupRepository: LocalBackupRepository,
    val coroutineScope: kotlinx.coroutines.CoroutineScope,
    val storedBooks: List<BookEntity>,
    val storedCollections: List<CollectionEntity>,
    val books: List<LocalBook>,
    val collections: List<String>,
    val todaySeconds: Long,
    val readingDayKeys: List<String>,
    val scannerState: ScannerUiState,
    val bookshelfLayout: BookshelfLayout,
    val documentActions: LibraryDocumentActions,
    val queryState: MutableState<String>,
    val selectedFilterState: MutableState<LibraryFilter>,
    val selectedCollectionState: MutableState<String?>,
    val collectionMenuExpandedState: MutableState<Boolean>,
    val showCollectionsManagerState: MutableState<Boolean>,
    val collectionBeingRenamedState: MutableState<CollectionEntity?>,
    val collectionRenameDraftState: MutableState<String>,
    val organizingBookState: MutableState<LocalBook?>,
    val collectionDraftState: MutableState<String>,
    val showLibraryStatsState: MutableState<Boolean>,
    val showGoalEditorState: MutableState<Boolean>,
    val showResetStatsDialogState: MutableState<Boolean>,
    val goalDraftState: MutableState<String>,
    val dailyGoalMinutesState: MutableIntState,
    val pendingRemovalState: MutableState<LocalBook?>,
    val editingBookState: MutableState<LocalBook?>,
    val titleDraftState: MutableState<String>,
    val sortMenuExpandedState: MutableState<Boolean>,
    val sortModeState: MutableState<LibrarySortMode>,
    val pendingMarkdownState: MutableState<String?>,
    val showBackupOptionsState: MutableState<Boolean>,
    val backupPreviewState: MutableState<LocalBackupPreview?>,
    val backupPasswordUriState: MutableState<Uri?>,
    val backupBusyState: MutableState<Boolean>,
    val viewModeState: MutableState<LibraryViewMode>,
    val savingBookUriState: MutableState<String?>,
    val onOfflineBooksChanged: () -> Unit,
    val persistBookshelfLayout: (BookshelfLayout) -> Unit,
    val selectSortMode: (LibrarySortMode) -> Unit,
    val resetBookshelfLayout: () -> Unit,
)

@Composable
internal fun rememberLibraryScreenState(
    viewModel: LibraryViewModel,
    settings: SettingsRepository,
    backupRepository: LocalBackupRepository,
): LibraryScreenState {
    val context = LocalContext.current
    val reading = rememberLibraryReadingState(viewModel)
    val todaySeconds = reading.todaySeconds
    val readingDayKeys = reading.dayKeys
    val libraryState by viewModel.state.collectAsStateWithLifecycle()
    val storedBooks = libraryState.books
    val storedCollections = libraryState.collections
    var offlineCopyUris by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var offlineRevision by remember { mutableIntStateOf(0) }
    val savingBookUriState = remember { mutableStateOf<String?>(null) }
    val bookUris = remember(storedBooks) { storedBooks.map { Uri.parse(it.uri) } }
    // A locator/progress update invalidates Room, but does not change the URI list.
    // Only rescan the private offline directory when membership/copies change.
    LaunchedEffect(context, bookUris, offlineRevision) {
        offlineCopyUris = withContext(Dispatchers.IO) {
            OfflineBookStore.findCopies(context, bookUris)
                .mapValues { (_, file) -> Uri.fromFile(file) }
        }
    }
    val books = remember(storedBooks, offlineCopyUris, libraryState.bookCollectionNames) {
        storedBooks.map {
            LocalBook(
                it.id, Uri.parse(it.uri), it.title, it.format, it.isFavorite, it.importedAt, it.lastOpenedAt,
                it.readProgress, it.collectionId, offlineCopyUris[it.uri],
                libraryState.bookCollectionNames[it.id].orEmpty(),
            )
        }
    }
    val collections = remember(storedCollections) { storedCollections.map { it.name } }
    val coroutineScope = rememberCoroutineScope()
    val queryState = rememberSaveable { mutableStateOf("") }
    val selectedFilterState = rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    val selectedCollectionState = rememberSaveable { mutableStateOf<String?>(null) }
    val collectionMenuExpandedState = remember { mutableStateOf(false) }
    val showCollectionsManagerState = remember { mutableStateOf(false) }
    val collectionBeingRenamedState = remember { mutableStateOf<CollectionEntity?>(null) }
    val collectionRenameDraftState = remember { mutableStateOf("") }
    val organizingBookIdState = rememberSaveable { mutableStateOf<String?>(null) }
    val organizingBookState = remember { mutableStateOf<LocalBook?>(null) }
    var organizingBook by organizingBookState
    LaunchedEffect(organizingBook, organizingBookIdState.value, books) {
        val selectedId = organizingBookIdState.value
        if (organizingBook == null && selectedId != null) {
            organizingBook = books.firstOrNull { it.id == selectedId }
        } else if (organizingBook != null && selectedId != organizingBook?.id) {
            organizingBookIdState.value = organizingBook?.id
        } else if (organizingBook == null && selectedId != null && books.any { it.id == selectedId }) {
            organizingBookIdState.value = null
        }
    }
    val collectionDraftState = remember { mutableStateOf("") }
    val showLibraryStatsState = remember { mutableStateOf(false) }
    val showGoalEditorState = remember { mutableStateOf(false) }
    val showResetStatsDialogState = remember { mutableStateOf(false) }
    val goalDraftState = remember { mutableStateOf("") }
    val dailyGoalMinutesState = remember {
        mutableIntStateOf(
            settings.getInt(SettingsRepository.Store.READING_STATS, "daily_goal_minutes", DEFAULT_DAILY_GOAL_MINUTES)
                .coerceIn(MIN_DAILY_GOAL_MINUTES, MAX_DAILY_GOAL_MINUTES),
        )
    }
    val pendingRemovalState = remember { mutableStateOf<LocalBook?>(null) }
    val editingBookState = remember { mutableStateOf<LocalBook?>(null) }
    val titleDraftState = remember { mutableStateOf("") }
    val sortMenuExpandedState = remember { mutableStateOf(false) }
    val sortModeState = rememberSaveable {
        val saved = settings.getString(SettingsRepository.Store.LIBRARY, BOOKSHELF_SORT_MODE_KEY)
        mutableStateOf(runCatching { LibrarySortMode.valueOf(saved.orEmpty()) }.getOrDefault(LibrarySortMode.TITLE_ASC))
    }
    // Persist only the stable book id; markdown is regenerated after the SAF picker returns.
    val pendingMarkdownState = rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMarkdown by pendingMarkdownState
    val cleanupMutex = remember { Mutex() }
    val showBackupOptionsState = remember { mutableStateOf(false) }
    val backupPreviewState = remember { mutableStateOf<LocalBackupPreview?>(null) }
    var backupPreview by backupPreviewState
    val backupPasswordUriState = remember { mutableStateOf<Uri?>(null) }
    val backupBusyState = remember { mutableStateOf(false) }
    var backupBusy by backupBusyState
    // The folder scan lives in the ViewModel so rotation no longer cancels it (A-36).
    val scannerState by viewModel.scanner.collectAsStateWithLifecycle()
    val viewModeState = rememberSaveable {
        val saved = settings.getString(SettingsRepository.Store.LIBRARY, BOOKSHELF_VIEW_MODE_KEY)
        mutableStateOf(runCatching { LibraryViewMode.valueOf(saved.orEmpty()) }.getOrDefault(LibraryViewMode.COVERS))
    }
    val bookshelfLayoutJsonState = remember {
        mutableStateOf(settings.getString(SettingsRepository.Store.LIBRARY, BOOKSHELF_LAYOUT_KEY))
    }
    val libraryBookIds = remember(books) { books.map(LocalBook::id) }
    val bookshelfLayout = remember(bookshelfLayoutJsonState.value, libraryBookIds) {
        BookshelfLayout.decode(bookshelfLayoutJsonState.value)?.reconcile(libraryBookIds)
            ?: BookshelfLayout.automatic(libraryBookIds)
    }

    fun persistBookshelfLayout(layout: BookshelfLayout) {
        val reconciled = layout.reconcile(libraryBookIds)
        val encoded = BookshelfLayout.encode(reconciled)
        bookshelfLayoutJsonState.value = encoded
        settings.apply(SettingsRepository.Store.LIBRARY) { putString(BOOKSHELF_LAYOUT_KEY, encoded) }
    }

    fun resetBookshelfLayout() {
        bookshelfLayoutJsonState.value = null
        settings.apply(SettingsRepository.Store.LIBRARY) { remove(BOOKSHELF_LAYOUT_KEY) }
    }

    fun selectSortMode(mode: LibrarySortMode) {
        sortModeState.value = mode
        settings.apply(SettingsRepository.Store.LIBRARY) { putString(BOOKSHELF_SORT_MODE_KEY, mode.name) }
    }

    LaunchedEffect(libraryState.isLoaded, libraryBookIds, bookshelfLayoutJsonState.value) {
        if (libraryState.isLoaded) {
            val existing = BookshelfLayout.decode(bookshelfLayoutJsonState.value)
            if (existing != null) {
                val reconciled = existing.reconcile(libraryBookIds)
                val encoded = BookshelfLayout.encode(reconciled)
                if (encoded != bookshelfLayoutJsonState.value) {
                    bookshelfLayoutJsonState.value = encoded
                    settings.apply(SettingsRepository.Store.LIBRARY) { putString(BOOKSHELF_LAYOUT_KEY, encoded) }
                }
            }
        }
    }

    LaunchedEffect(scannerState.error) {
        val message = scannerState.error ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.dismissScanError()
    }

    suspend fun drainPendingBookCleanup() {
        cleanupMutex.withLock {
            // One directory rescan for the whole batch, not one per deleted book (A-38).
            val completed = withContext(Dispatchers.IO) {
                var cleaned = 0
                viewModel.pendingCleanupUris().forEach { uri ->
                    if (DeletedBookCleanup.complete(context, uri, settings, viewModel::completeCleanup)) {
                        cleaned++
                    }
                }
                cleaned
            }
            if (completed > 0) offlineRevision++
        }
    }

    val welcomeBookFailedMessage = androidx.compose.ui.res.stringResource(R.string.library_welcome_book_failed)

    LaunchedEffect(context, viewModel, settings) {
        withContext(Dispatchers.IO) { OfflineBookStore.purgeOrphanedParts(context) }
        migrateLegacyLibrary(context, viewModel)
        drainPendingBookCleanup()
        if (!BundledWelcomeBook.installIfNeeded(
                context,
                settings,
                viewModel::findBook,
                viewModel::insertBook,
                viewModel::rebindUri,
            )
        ) {
            Toast.makeText(
                context,
                welcomeBookFailedMessage,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // A cleanup that fails while the library is already open (locked file, no
    // space) is retried the next time the app comes back, not only after a
    // process restart.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { drainPendingBookCleanup() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val documentActions = rememberLibraryDocumentActions(
        context = context,
        storedBooks = storedBooks,
        backupRepository = backupRepository,
        viewModel = viewModel,
        coroutineScope = coroutineScope,
        pendingMarkdown = pendingMarkdownState,
        backupBusy = backupBusyState,
        backupPreview = backupPreviewState,
        backupPasswordUri = backupPasswordUriState,
        organizingBook = organizingBookState,
        onOfflineBooksChanged = { offlineRevision++ },
    )

    return LibraryScreenState(
        context = context,
        viewModel = viewModel,
        settings = settings,
        backupRepository = backupRepository,
        coroutineScope = coroutineScope,
        storedBooks = storedBooks,
        storedCollections = storedCollections,
        books = books,
        collections = collections,
        todaySeconds = todaySeconds,
        readingDayKeys = readingDayKeys,
        scannerState = scannerState,
        bookshelfLayout = bookshelfLayout,
        documentActions = documentActions,
        queryState = queryState,
        selectedFilterState = selectedFilterState,
        selectedCollectionState = selectedCollectionState,
        collectionMenuExpandedState = collectionMenuExpandedState,
        showCollectionsManagerState = showCollectionsManagerState,
        collectionBeingRenamedState = collectionBeingRenamedState,
        collectionRenameDraftState = collectionRenameDraftState,
        organizingBookState = organizingBookState,
        collectionDraftState = collectionDraftState,
        showLibraryStatsState = showLibraryStatsState,
        showGoalEditorState = showGoalEditorState,
        showResetStatsDialogState = showResetStatsDialogState,
        goalDraftState = goalDraftState,
        dailyGoalMinutesState = dailyGoalMinutesState,
        pendingRemovalState = pendingRemovalState,
        editingBookState = editingBookState,
        titleDraftState = titleDraftState,
        sortMenuExpandedState = sortMenuExpandedState,
        sortModeState = sortModeState,
        pendingMarkdownState = pendingMarkdownState,
        showBackupOptionsState = showBackupOptionsState,
        backupPreviewState = backupPreviewState,
        backupPasswordUriState = backupPasswordUriState,
        backupBusyState = backupBusyState,
        viewModeState = viewModeState,
        savingBookUriState = savingBookUriState,
        onOfflineBooksChanged = { offlineRevision++ },
        persistBookshelfLayout = ::persistBookshelfLayout,
        selectSortMode = ::selectSortMode,
        resetBookshelfLayout = ::resetBookshelfLayout,
    )
}
