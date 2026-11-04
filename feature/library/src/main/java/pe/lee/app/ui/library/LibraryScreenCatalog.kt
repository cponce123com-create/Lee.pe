package pe.lee.app.ui.library

import android.net.Uri
import pe.lee.app.data.repository.SettingsRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Presents the catalog and runs cancellable filtering away from the UI thread. */
@Composable
internal fun LibraryScreenCatalog(
    state: LibraryScreenState,
    onOpenBook: (Uri, String, String, Uri?) -> Unit,
) = with(state) {
    // The catalog pipeline runs on Dispatchers.Default: with 10 000 books it costs
    // hundreds of milliseconds and used to block the main thread on every Room
    // emission (menus, dialogs and stats updates kept it recomputed) (P-06).
    val titleComparator = remember { libraryTitleComparator() }
    var catalog by remember {
        mutableStateOf(CatalogResult(emptyList(), emptyList(), emptyList()))
    }
    val query = queryState.value
    val selectedFilter = selectedFilterState.value
    val selectedCollection = selectedCollectionState.value
    val sortMode = sortModeState.value
    // Index construction stays off the main thread and is reused while typing.
    var indexedBooks by remember { mutableStateOf<Pair<List<LocalBook>, pe.lee.core.domain.library.SearchBooksUseCase<LocalBook>>?>(null) }
    LaunchedEffect(books) {
        indexedBooks = withContext(Dispatchers.Default) { books to librarySearchIndex(books) }
    }
    LaunchedEffect(books, indexedBooks, query, selectedFilter, selectedCollection, sortMode, titleComparator) {
        val index = indexedBooks?.takeIf { it.first == books }?.second ?: return@LaunchedEffect
        catalog = withContext(Dispatchers.Default) {
            computeCatalog(books, query, selectedFilter, selectedCollection, sortMode, titleComparator, index)
        }
    }
    val visibleBooks = catalog.visible
    val orderedBooks = catalog.ordered

    LibraryCatalog(
        books = books,
        visibleBooks = visibleBooks,
        orderedBooks = orderedBooks,
        collections = collections,
        query = queryState,
        selectedFilter = selectedFilterState,
        selectedCollection = selectedCollectionState,
        collectionMenuExpanded = collectionMenuExpandedState,
        showCollectionsManager = showCollectionsManagerState,
        sortMenuExpanded = sortMenuExpandedState,
        sortMode = sortModeState,
        viewMode = viewModeState,
        bookshelfLayout = bookshelfLayout,
        onViewModeChange = { mode ->
            viewModeState.value = mode
            settings.apply(SettingsRepository.Store.LIBRARY) { putString(BOOKSHELF_VIEW_MODE_KEY, mode.name) }
        },
        onBookshelfLayoutChange = persistBookshelfLayout,
        onCustomLayoutSelected = { selectSortMode(LibrarySortMode.CUSTOM) },
        onSortModeChange = selectSortMode,
        onResetBookshelf = resetBookshelfLayout,
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
        onStats = { showLibraryStatsState.value = true },
        onBackup = { showBackupOptionsState.value = true },
        onOpen = { book -> onOpenBook(book.uri, book.format, book.title, book.offlineCopyUri) },
        onOfflineBooksChanged = onOfflineBooksChanged,
        onExportAnnotationsFile = documentActions.exportAnnotationsFile
    )
}
