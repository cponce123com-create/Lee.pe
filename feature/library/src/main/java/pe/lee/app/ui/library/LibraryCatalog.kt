package pe.lee.app.ui.library

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.feature.library.R

private val ShelfPage = Color(0xFF2C1E18)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun LibraryCatalog(
    books: List<LocalBook>,
    visibleBooks: List<LocalBook>,
    orderedBooks: List<LocalBook>,
    collections: List<String>,
    query: MutableState<String>,
    selectedFilter: MutableState<LibraryFilter>,
    selectedCollection: MutableState<String?>,
    collectionMenuExpanded: MutableState<Boolean>,
    showCollectionsManager: MutableState<Boolean>,
    sortMenuExpanded: MutableState<Boolean>,
    sortMode: MutableState<LibrarySortMode>,
    viewMode: MutableState<LibraryViewMode>,
    bookshelfLayout: BookshelfLayout,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onBookshelfLayoutChange: (BookshelfLayout) -> Unit,
    onCustomLayoutSelected: () -> Unit,
    onSortModeChange: (LibrarySortMode) -> Unit,
    onResetBookshelf: () -> Unit,
    savingBookUri: MutableState<String?>,
    pendingRemoval: MutableState<LocalBook?>,
    editingBook: MutableState<LocalBook?>,
    titleDraft: MutableState<String>,
    pendingMarkdown: MutableState<String?>,
    organizingBook: MutableState<LocalBook?>,
    collectionDraft: MutableState<String>,
    scannerBusy: Boolean,
    scannerImporting: Boolean,
    context: Context,
    viewModel: LibraryViewModel,
    coroutineScope: CoroutineScope,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onStats: () -> Unit,
    onBackup: () -> Unit,
    onOpen: (LocalBook) -> Unit,
    onOfflineBooksChanged: () -> Unit,
    onExportAnnotationsFile: (String) -> Unit,
) {
    val pendingOfflineCopyRemoval = remember { mutableStateOf<LocalBook?>(null) }
    var showBookshelfCustomizer by rememberSaveable { mutableStateOf(false) }
    var showHiddenBooks by rememberSaveable { mutableStateOf(false) }
    val replacementDecorationId = rememberSaveable { mutableStateOf<String?>(null) }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val colors = MaterialTheme.colorScheme
    val darkTheme = isSystemInDarkTheme()

    fun handleBookAction(book: LocalBook, action: LibraryBookAction) {
        when (action) {
            LibraryBookAction.OPEN -> onOpen(book)
            LibraryBookAction.REMOVE -> pendingRemoval.value = book
            LibraryBookAction.REMOVE_OFFLINE_COPY -> pendingOfflineCopyRemoval.value = book
            LibraryBookAction.SAVE_OFFLINE_COPY -> {
                val key = book.uri.toString()
                if (savingBookUri.value != key) {
                    savingBookUri.value = key
                    coroutineScope.launch {
                        try {
                            val displayName = withContext(Dispatchers.IO) { queryDisplayName(context, book.uri) }
                            OfflineBookStore.saveCopy(context, book.uri, displayName)
                            onOfflineBooksChanged()
                            Toast.makeText(context, context.getString(R.string.library_offline_copy_saved), Toast.LENGTH_SHORT).show()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(storageErrorMessage(error, R.string.library_offline_copy_save_failed)),
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            savingBookUri.value = null
                        }
                    }
                }
            }
            LibraryBookAction.EXPORT_ANNOTATIONS -> coroutineScope.launch {
                try {
                    val annotations = viewModel.annotationsForBook(book.uri.toString())
                    if (annotations.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.library_no_annotations), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingMarkdown.value = book.id
                        onExportAnnotationsFile(
                            annotationExportFileName(
                                book.title,
                                context.getString(pe.lee.feature.notes.R.string.annotation_default_book),
                            ),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.library_annotations_read_failed), Toast.LENGTH_LONG).show()
                }
            }
            LibraryBookAction.TOGGLE_FAVORITE -> coroutineScope.launch {
                viewModel.updateFavorite(book.uri.toString(), !book.isFavorite)
            }
            LibraryBookAction.EDIT_TITLE -> {
                editingBook.value = book
                titleDraft.value = book.title
            }
            LibraryBookAction.ORGANIZE -> {
                organizingBook.value = book
                collectionDraft.value = book.collectionNames.firstOrNull().orEmpty()
            }
        }
    }

    val coverRows = remember(orderedBooks) { orderedBooks.chunked(3) }
    val configuration = LocalConfiguration.current
    val naturalCabinetHeight = (configuration.screenWidthDp - 12).coerceAtMost(600).dp * 1.5f
    val availableCabinetHeight = (configuration.screenHeightDp - 220).coerceAtLeast(260).dp
    val shelfHeight = minOf(naturalCabinetHeight, availableCabinetHeight)
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val customOrder = sortMode.value == LibrarySortMode.CUSTOM
    val shelfBooks = remember(orderedBooks, bookshelfLayout.hiddenBookIds, customOrder) {
        if (customOrder) orderedBooks.filterNot { it.id in bookshelfLayout.hiddenBookIds } else orderedBooks
    }
    val booksById = remember(books) { books.associateBy(LocalBook::id) }
    val shelves = remember(shelfBooks, bookshelfLayout, customOrder) { buildShelfDisplay(shelfBooks, bookshelfLayout, customOrder) }
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag("library_catalog")
            .background(if (darkTheme) colors.background else ShelfPage)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 6.dp),
        contentPadding = PaddingValues(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item(key = "dashboard") {
            LibraryDashboardHeader(
                totalBooks = books.size,
                busy = scannerBusy || scannerImporting,
                onImport = onImport,
                onScan = onScan,
                onStats = onStats,
                onBackup = onBackup,
                onSearch = { searchExpanded = !searchExpanded },
            )
        }
        if (searchExpanded || query.value.isNotBlank()) {
            item(key = "search") {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(
                            if (darkTheme) colors.surfaceVariant else colors.surfaceVariant,
                        ).padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = context.getString(R.string.accessibility_search), tint = colors.primary)
                    Spacer(Modifier.width(9.dp))
                    BasicTextField(
                        value = query.value,
                        onValueChange = { query.value = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = context.getString(R.string.accessibility_search_books)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                        decorationBox = { inner ->
                            if (query.value.isEmpty()) {
                                Text(
                                    stringResource(R.string.library_search_placeholder),
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                    if (query.value.isNotBlank()) {
                        IconButton(onClick = { query.value = "" }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = context.getString(R.string.accessibility_clear_search),
                                tint = colors.primary,
                            )
                        }
                    }
                }
            }
        }
        item(key = "filters") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(LibraryFilter.ALL, LibraryFilter.TO_READ, LibraryFilter.IN_PROGRESS, LibraryFilter.COMPLETED).forEach { filter ->
                    FilterChip(
                        selected = selectedFilter.value == filter,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF6CD73),
                            selectedLabelColor = Color(0xFF392614),
                            labelColor = Color(0xFFFFEBD0),
                            containerColor = Color(0x44271D17),
                        ),
                        onClick = { selectedFilter.value = filter },
                        label = {
                            Text(
                                stringResource(
                                    if (filter ==
                                        LibraryFilter.COMPLETED
                                    ) {
                                        R.string.library_filter_completed_short
                                    } else {
                                        filter.labelRes
                                    },
                                ),
                                fontSize = if (screenWidth < 360) 10.sp else 12.sp,
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
            }
        }
        item(key = "toolbar") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                LibraryViewModeSelector(
                    viewMode = viewMode.value,
                    onModeChange = onViewModeChange,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { sortMenuExpanded.value = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(
                            stringResource(R.string.library_sort_order, stringResource(sortMode.value.labelRes)),
                            color = colors.primary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(expanded = sortMenuExpanded.value, onDismissRequest = { sortMenuExpanded.value = false }) {
                        LibrarySortMode.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(stringResource(option.labelRes)) }, onClick = {
                                onSortModeChange(option)
                                sortMenuExpanded.value = false
                            })
                        }
                        androidx.compose.material3.HorizontalDivider()
                        LibraryFilter.entries.filterNot {
                            it in listOf(LibraryFilter.ALL, LibraryFilter.TO_READ, LibraryFilter.IN_PROGRESS, LibraryFilter.COMPLETED)
                        }.forEach { filter ->
                            DropdownMenuItem(text = { Text(stringResource(filter.labelRes)) }, onClick = {
                                selectedFilter.value = filter
                                sortMenuExpanded.value = false
                            })
                        }
                    }
                }
                if (collections.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { collectionMenuExpanded.value = true }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                            Text(
                                selectedCollection.value ?: stringResource(R.string.library_collection),
                                color = colors.primary,
                                maxLines = 1,
                            )
                        }
                        DropdownMenu(expanded = collectionMenuExpanded.value, onDismissRequest = { collectionMenuExpanded.value = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.library_all_collections)) }, onClick = {
                                selectedCollection.value = null
                                collectionMenuExpanded.value = false
                            })
                            collections.forEach { collection ->
                                DropdownMenuItem(text = { Text(collection) }, onClick = {
                                    selectedCollection.value = collection
                                    collectionMenuExpanded.value = false
                                })
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.library_manage_collections)) }, onClick = {
                                showCollectionsManager.value = true
                                collectionMenuExpanded.value = false
                            })
                        }
                    }
                }
                if (viewMode.value == LibraryViewMode.MIXED_BOOKSHELF) {
                    TextButton(onClick = { showBookshelfCustomizer = true }, contentPadding = PaddingValues(horizontal = 5.dp)) {
                        Text(
                            stringResource(R.string.shelf_decoration_count, bookshelfLayout.decorations.size),
                            color = colors.primary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        if (visibleBooks.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth()) {
                    CoverShelfPager(emptyList(), shelfHeight, null, { _, _ -> }, {})
                    TextButton(
                        onClick = {
                            if (books.isEmpty()) {
                                onImport()
                            } else {
                                query.value = ""
                                selectedFilter.value = LibraryFilter.ALL
                                selectedCollection.value = null
                            }
                        },
                        modifier = Modifier.align(Alignment.Center)
                            .background(Color(0xDD2C1E18), RoundedCornerShape(12.dp)),
                    ) {
                        Text(stringResource(if (books.isEmpty()) R.string.library_choose_files else R.string.library_try_another_search))
                    }
                }
            }
        } else if (viewMode.value == LibraryViewMode.COVERS) {
            item(key = "cover-pages") {
                CoverShelfPager(
                    books = orderedBooks,
                    height = shelfHeight,
                    savingBookUri = savingBookUri.value,
                    onBookAction = ::handleBookAction,
                    onReorder = { ids ->
                        onBookshelfLayoutChange(bookshelfLayout.reorderBooks(ids))
                        onCustomLayoutSelected()
                    },
                )
            }
        } else {
            item(key = "interactive-shelves") {
                InteractiveMixedShelf(
                    layout = bookshelfLayout,
                    shelves = shelves,
                    booksById = booksById,
                    height = shelfHeight,
                    onOpen = { handleBookAction(it, LibraryBookAction.OPEN) },
                    onCommit = {
                        onBookshelfLayoutChange(it)
                        onCustomLayoutSelected()
                    },
                    onDecorationClick = {
                        replacementDecorationId.value = it
                        showBookshelfCustomizer = true
                    },
                )
            }
        }
        item(key = "privacy") {
            Text(
                stringResource(R.string.library_local_privacy_note),
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    pendingOfflineCopyRemoval.value?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingOfflineCopyRemoval.value = null },
            title = { Text(stringResource(R.string.library_remove_offline_copy_title)) },
            text = { Text(stringResource(R.string.library_remove_offline_copy_message, book.title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingOfflineCopyRemoval.value = null
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { OfflineBookStore.deleteCopy(context, book.uri) }
                        onOfflineBooksChanged()
                        Toast.makeText(context, context.getString(R.string.library_offline_copy_removed), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingOfflineCopyRemoval.value = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showBookshelfCustomizer) {
        BookshelfCustomizerDialog(
            decorationCount = bookshelfLayout.decorations.size,
            viewMode = viewMode.value,
            onViewModeChange = onViewModeChange,
            coverRows = coverRows,
            shelves = shelves,
            booksById = booksById,
            savingBookUri = savingBookUri.value,
            onBookAction = ::handleBookAction,
            onMoveBook = { id, shelfDelta, orderDelta ->
                onBookshelfLayoutChange(bookshelfLayout.moveBook(id, shelfDelta, orderDelta))
                onCustomLayoutSelected()
            },
            onToggleOrientation = { id ->
                onBookshelfLayoutChange(bookshelfLayout.toggleOrientation(id))
                onCustomLayoutSelected()
            },
            onRemoveBook = { id ->
                onBookshelfLayoutChange(bookshelfLayout.removeFromShelf(id))
                onCustomLayoutSelected()
            },
            onMoveDecoration = { id, shelfDelta, orderDelta ->
                onBookshelfLayoutChange(bookshelfLayout.moveDecoration(id, shelfDelta, orderDelta))
                onCustomLayoutSelected()
            },
            onReplaceDecoration = { id, type ->
                onBookshelfLayoutChange(bookshelfLayout.replaceDecoration(id, type))
                onCustomLayoutSelected()
            },
            onRemoveDecoration = { id ->
                onBookshelfLayoutChange(bookshelfLayout.removeDecoration(id))
                onCustomLayoutSelected()
            },
            replacementDecorationId = replacementDecorationId.value,
            onAddDecoration = { type ->
                if (bookshelfLayout.decorations.size >= MAX_SHELF_DECORATIONS) {
                    Toast.makeText(context, R.string.shelf_decoration_limit, Toast.LENGTH_SHORT).show()
                } else {
                    val shelf = (bookshelfLayout.shelfCount() - 1).coerceAtLeast(0)
                    val nextOrder = (bookshelfLayout.objectsOnShelf(shelf).maxOfOrNull(ShelfObject::order) ?: -1) + 1
                    onBookshelfLayoutChange(bookshelfLayout.addDecoration(type, shelf, nextOrder))
                    onCustomLayoutSelected()
                    showBookshelfCustomizer = false
                }
            },
            onAddBooks = { showHiddenBooks = true },
            onReset = {
                onResetBookshelf()
                onSortModeChange(LibrarySortMode.TITLE_ASC)
            },
            onSave = {
                showBookshelfCustomizer = false
                replacementDecorationId.value = null
            },
        )
    }
    if (showHiddenBooks) {
        BookshelfHiddenBooksDialog(
            books = books.filter { it.id in bookshelfLayout.hiddenBookIds },
            onAdd = { book ->
                onBookshelfLayoutChange(bookshelfLayout.addToShelf(book.id, (bookshelfLayout.shelfCount() - 1).coerceAtLeast(0)))
                onCustomLayoutSelected()
            },
            onDismiss = { showHiddenBooks = false },
        )
    }
}
