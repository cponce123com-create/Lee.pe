package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import pe.lee.app.data.repository.AudioProgressStore
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.CollectionEntity
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.data.repository.LocalBackupRepository
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.lee.feature.library.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DEFAULT_DAILY_GOAL_MINUTES = 30
private const val MIN_DAILY_GOAL_MINUTES = 5
private const val MAX_DAILY_GOAL_MINUTES = 240



internal data class LocalBook(
    val id: String,
    val uri: Uri,
    val title: String,
    val format: String,
    val isFavorite: Boolean,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val readProgress: Float,
    val collectionId: String?,
    val offlineCopyUri: Uri?
)

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    settings: SettingsRepository,
    backupRepository: LocalBackupRepository,
    onOpenBook: (uri: Uri, format: String, title: String, offlineCopyUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    val todayKeyState = remember { mutableStateOf(readingDayKey(System.currentTimeMillis())) }
    val todayKey by todayKeyState
    // "Today" was computed once per composition: leaving the app open past
    // midnight kept showing yesterday's time and goal (A-37).
    LaunchedEffect(todayKey) {
        val now = System.currentTimeMillis()
        val nextMidnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        delay((nextMidnight - now).coerceAtLeast(1_000L))
        todayKeyState.value = readingDayKey(System.currentTimeMillis())
    }
    // A fresh Flow instance per recomposition restarted the Room subscription on
    // every keystroke (P-01); remember it for the lifetime of the day.
    val todaySecondsFlow = remember(todayKey) { viewModel.observeDurationForDay(todayKey) }
    val readingDayKeysFlow = remember { viewModel.observeReadingDayKeys() }
    val todaySeconds by todaySecondsFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val readingDayKeys by readingDayKeysFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryState by viewModel.state.collectAsStateWithLifecycle()
    val storedBooks = libraryState.books
    val storedCollections = libraryState.collections
    var offlineCopyUris by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var offlineRevision by remember { mutableIntStateOf(0) }
    val savingBookUriState = remember { mutableStateOf<String?>(null) }
    var savingBookUri by savingBookUriState
    val bookUris = remember(storedBooks) { storedBooks.map { Uri.parse(it.uri) } }
    // A locator/progress update invalidates Room, but does not change the URI list.
    // Only rescan the private offline directory when membership/copies change.
    LaunchedEffect(context, bookUris, offlineRevision) {
        offlineCopyUris = withContext(Dispatchers.IO) {
            OfflineBookStore.findCopies(context, bookUris)
                .mapValues { (_, file) -> Uri.fromFile(file) }
        }
    }
    val books = remember(storedBooks, offlineCopyUris) {
        storedBooks.map {
            LocalBook(it.id, Uri.parse(it.uri), it.title, it.format, it.isFavorite, it.importedAt, it.lastOpenedAt,
                it.readProgress, it.collectionId, offlineCopyUris[it.uri])
        }
    }
    val collections = remember(storedCollections) { storedCollections.map { it.name } }
    val coroutineScope = rememberCoroutineScope()
    val queryState = rememberSaveable { mutableStateOf("") }
    var query by queryState
    val selectedFilterState = rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var selectedFilter by selectedFilterState
    val selectedCollectionState = rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCollection by selectedCollectionState
    val collectionMenuExpandedState = remember { mutableStateOf(false) }
    var collectionMenuExpanded by collectionMenuExpandedState
    val showCollectionsManagerState = remember { mutableStateOf(false) }
    var showCollectionsManager by showCollectionsManagerState
    val collectionBeingRenamedState = remember { mutableStateOf<CollectionEntity?>(null) }
    var collectionBeingRenamed by collectionBeingRenamedState
    val collectionRenameDraftState = remember { mutableStateOf("") }
    var collectionRenameDraft by collectionRenameDraftState
    val organizingBookState = remember { mutableStateOf<LocalBook?>(null) }
    var organizingBook by organizingBookState
    val collectionDraftState = remember { mutableStateOf("") }
    var collectionDraft by collectionDraftState
    val showLibraryStatsState = remember { mutableStateOf(false) }
    var showLibraryStats by showLibraryStatsState
    val showGoalEditorState = remember { mutableStateOf(false) }
    var showGoalEditor by showGoalEditorState
    val showResetStatsDialogState = remember { mutableStateOf(false) }
    var showResetStatsDialog by showResetStatsDialogState
    val goalDraftState = remember { mutableStateOf("") }
    var goalDraft by goalDraftState
    val dailyGoalMinutesState = remember {
        mutableIntStateOf(
            settings.getInt(SettingsRepository.Store.READING_STATS, "daily_goal_minutes", DEFAULT_DAILY_GOAL_MINUTES)
                .coerceIn(MIN_DAILY_GOAL_MINUTES, MAX_DAILY_GOAL_MINUTES)
        )
    }
    var dailyGoalMinutes by dailyGoalMinutesState
    val pendingRemovalState = remember { mutableStateOf<LocalBook?>(null) }
    var pendingRemoval by pendingRemovalState
    val editingBookState = remember { mutableStateOf<LocalBook?>(null) }
    var editingBook by editingBookState
    val titleDraftState = remember { mutableStateOf("") }
    var titleDraft by titleDraftState
    val sortMenuExpandedState = remember { mutableStateOf(false) }
    var sortMenuExpanded by sortMenuExpandedState
    val sortModeState = rememberSaveable { mutableStateOf(LibrarySortMode.TITLE_ASC) }
    var sortMode by sortModeState
    val pendingMarkdownState = remember { mutableStateOf<String?>(null) }
    var pendingMarkdown by pendingMarkdownState
    var libraryMenuExpanded by remember { mutableStateOf(false) }
    val showBackupOptionsState = remember { mutableStateOf(false) }
    var showBackupOptions by showBackupOptionsState
    val backupPreviewState = remember { mutableStateOf<LocalBackupPreview?>(null) }
    var backupPreview by backupPreviewState
    val backupBusyState = remember { mutableStateOf(false) }
    var backupBusy by backupBusyState
    // The folder scan lives in the ViewModel so rotation no longer cancels it (A-36).
    val scannerState by viewModel.scanner.collectAsStateWithLifecycle()
    val listModeState = rememberSaveable { mutableStateOf(false) }
    var listMode by listModeState

    LaunchedEffect(scannerState.error) {
        val message = scannerState.error ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.dismissScanError()
    }

    suspend fun drainPendingBookCleanup() {
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

    LaunchedEffect(context, viewModel, settings) {
        migrateLegacyLibrary(context, viewModel)
        drainPendingBookCleanup()
        if (!BundledWelcomeBook.installIfNeeded(
                context,
                settings,
                viewModel::findBook,
                viewModel::insertBook,
                viewModel::rebindUri
            )) {
            Toast.makeText(
                context,
                context.getString(R.string.library_welcome_book_failed),
                Toast.LENGTH_LONG
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
        organizingBook = organizingBookState,
        onOfflineBooksChanged = { offlineRevision++ }
    )

    LibraryProcessDialogs(
        context = context,
        viewModel = viewModel,
        backupRepository = backupRepository,
        coroutineScope = coroutineScope,
        storedBooks = storedBooks,
        scanner = scannerState,
        onCancelScan = viewModel::cancelScan,
        onClearScanPreview = viewModel::clearScanPreview,
        showBackupOptions = showBackupOptionsState,
        backupBusy = backupBusyState,
        backupPreview = backupPreviewState,
        onExportBackup = documentActions.exportBackup,
        onImportBackup = documentActions.importBackup
    )

    LibraryStatisticsDialogs(
        books = books,
        todaySeconds = todaySeconds,
        readingDayKeys = readingDayKeys,
        showLibraryStats = showLibraryStatsState,
        showGoalEditor = showGoalEditorState,
        showResetStatsDialog = showResetStatsDialogState,
        goalDraft = goalDraftState,
        dailyGoalMinutes = dailyGoalMinutesState,
        settings = settings,
        viewModel = viewModel,
        context = context,
        coroutineScope = coroutineScope
    )

    LibraryManagementDialogs(
        context = context,
        viewModel = viewModel,
        settings = settings,
        coroutineScope = coroutineScope,
        pendingRemoval = pendingRemovalState,
        organizingBook = organizingBookState,
        collections = collections,
        collectionDraft = collectionDraftState,
        showCollectionsManager = showCollectionsManagerState,
        storedCollections = storedCollections,
        storedBooks = books,
        collectionBeingRenamed = collectionBeingRenamedState,
        collectionRenameDraft = collectionRenameDraftState,
        editingBook = editingBookState,
        titleDraft = titleDraftState,
        selectedCollection = selectedCollectionState,
        onRelinkFile = documentActions.relinkBook,
        onOfflineBooksChanged = { offlineRevision++ }
    )

    // The catalog pipeline runs on Dispatchers.Default: with 10 000 books it costs
    // hundreds of milliseconds and used to block the main thread on every Room
    // emission (menus, dialogs and stats updates kept it recomputed) (P-06).
    val titleComparator = remember { libraryTitleComparator() }
    var catalog by remember {
        mutableStateOf(CatalogResult(emptyList(), emptyList(), emptyList()))
    }
    LaunchedEffect(books, query, selectedFilter, selectedCollection, sortMode, titleComparator) {
        catalog = withContext(Dispatchers.Default) {
            computeCatalog(books, query, selectedFilter, selectedCollection, sortMode, titleComparator)
        }
    }
    val visibleBooks = catalog.visible
    val continueBooks = catalog.continueReading
    val orderedBooks = catalog.ordered

    LibraryCatalog(
        books = books,
        visibleBooks = visibleBooks,
        continueBooks = continueBooks,
        orderedBooks = orderedBooks,
        collections = collections,
        query = queryState,
        selectedFilter = selectedFilterState,
        selectedCollection = selectedCollectionState,
        collectionMenuExpanded = collectionMenuExpandedState,
        showCollectionsManager = showCollectionsManagerState,
        sortMenuExpanded = sortMenuExpandedState,
        sortMode = sortModeState,
        listMode = listModeState,
        savingBookUri = savingBookUriState,
        pendingRemoval = pendingRemovalState,
        editingBook = editingBookState,
        titleDraft = titleDraftState,
        pendingMarkdown = pendingMarkdownState,
        organizingBook = organizingBookState,
        collectionDraft = collectionDraftState,
        scannerBusy = scannerState.busy,
        scannerImporting = scannerState.busy,
        context = context,
        viewModel = viewModel,
        coroutineScope = coroutineScope,
        onImport = documentActions.importDocuments,
        onScan = documentActions.scanDirectory,
        onStats = { showLibraryStats = true },
        onBackup = { showBackupOptions = true },
        onOpen = { book -> onOpenBook(book.uri, book.format, book.title, book.offlineCopyUri) },
        onOfflineBooksChanged = { offlineRevision++ },
        onExportAnnotationsFile = documentActions.exportAnnotationsFile
    )

}
