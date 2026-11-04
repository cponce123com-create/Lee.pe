package pe.lee.app.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Dialog composition is independent of the catalog list and its search pipeline. */
@Composable
internal fun LibraryScreenDialogs(state: LibraryScreenState) = with(state) {
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
        backupPasswordUri = backupPasswordUriState,
        onExportBackup = documentActions.exportBackup,
        onImportBackup = documentActions.importBackup,
        onUnlockBackup = documentActions.unlockBackup,
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
        coroutineScope = coroutineScope,
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
        onOfflineBooksChanged = onOfflineBooksChanged,
    )
}
