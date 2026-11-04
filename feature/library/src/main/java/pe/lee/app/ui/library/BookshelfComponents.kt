package pe.lee.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.lee.feature.library.R

internal data class ShelfDisplay(val index: Int, val objects: List<ShelfObject>)

internal fun buildShelfDisplay(
    orderedBooks: List<LocalBook>,
    layout: BookshelfLayout,
    customOrder: Boolean
): List<ShelfDisplay> {
    val booksById = orderedBooks.associateBy { it.id }
    if (customOrder) {
        return (0 until layout.shelfCount()).mapNotNull { shelf ->
            val objects = layout.objectsOnShelf(shelf).filter { item ->
                when (item) {
                    is ShelfObject.Book -> item.placement.bookId in booksById
                    is ShelfObject.Decor -> true
                }
            }
            objects.takeIf { it.isNotEmpty() }?.let { ShelfDisplay(shelf, it) }
        }
    }

    val placements = layout.books.associateBy { it.bookId }
    return orderedBooks.chunked(5).mapIndexed { shelf, books ->
        val bookObjects = books.mapIndexed { index, book ->
            ShelfObject.Book(
                placements[book.id]?.copy(shelf = shelf, order = index * 2)
                    ?: BookshelfBookPlacement(book.id, shelf, index * 2, BookOrientation.UPRIGHT)
            )
        }
        val decorationObjects = layout.decorations.filter { it.shelf == shelf }.map { decor ->
            ShelfObject.Decor(decor.copy(order = decor.order * 2 + 1))
        }
        ShelfDisplay(shelf, (bookObjects + decorationObjects).sortedBy(ShelfObject::order))
    }
}

@Composable
internal fun BookshelfRow(
    shelf: ShelfDisplay,
    booksById: Map<String, LocalBook>,
    savingBookUri: String?,
    onBookAction: (LocalBook, LibraryBookAction) -> Unit,
    onMoveBook: (String, Int, Int) -> Unit,
    onToggleOrientation: (String) -> Unit,
    onRemoveBook: (String) -> Unit,
    onMoveDecoration: (String, Int, Int) -> Unit,
    onReplaceDecoration: (String) -> Unit,
    onRemoveDecoration: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val wood = Brush.verticalGradient(listOf(Color(0xFFE6B878), Color(0xFFB8793E), Color(0xFF78451F)))
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(min = 154.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.Bottom
        ) {
            shelf.objects.forEach { item ->
                when (item) {
                    is ShelfObject.Book -> booksById[item.placement.bookId]?.let { book ->
                        if (item.placement.orientation == BookOrientation.UPRIGHT) {
                            SpineBook(
                                book = book,
                                savingBookUri = savingBookUri,
                                onAction = { onBookAction(book, it) },
                                onMove = { shelfDelta, orderDelta -> onMoveBook(book.id, shelfDelta, orderDelta) },
                                onToggleOrientation = { onToggleOrientation(book.id) },
                                onRemoveFromShelf = { onRemoveBook(book.id) }
                            )
                        } else {
                            StackedBook(
                                book = book,
                                savingBookUri = savingBookUri,
                                onAction = { onBookAction(book, it) },
                                onMove = { shelfDelta, orderDelta -> onMoveBook(book.id, shelfDelta, orderDelta) },
                                onToggleOrientation = { onToggleOrientation(book.id) },
                                onRemoveFromShelf = { onRemoveBook(book.id) }
                            )
                        }
                    }
                    is ShelfObject.Decor -> ShelfDecorationCard(
                        decoration = item.decoration,
                        onMove = { shelfDelta, orderDelta -> onMoveDecoration(item.decoration.id, shelfDelta, orderDelta) },
                        onReplace = { onReplaceDecoration(item.decoration.id) },
                        onRemove = { onRemoveDecoration(item.decoration.id) }
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 1.dp).height(12.dp)
                .shadow(5.dp, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
                .background(wood)
        )
    }
}

@Composable
private fun SpineBook(
    book: LocalBook,
    savingBookUri: String?,
    onAction: (LibraryBookAction) -> Unit,
    onMove: (shelfDelta: Int, orderDelta: Int) -> Unit,
    onToggleOrientation: () -> Unit,
    onRemoveFromShelf: () -> Unit
) {
    val colors = spinePalette(book.id)
    Box(
        Modifier.width(54.dp).height(134.dp).shadow(7.dp, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .background(Brush.horizontalGradient(colors))
            .clickable(onClickLabel = book.title, onClick = { onAction(LibraryBookAction.OPEN) })
            .semantics { role = Role.Button; contentDescription = book.title },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter).background(Color(0x66FFF0C7)))
        Text(
            text = book.title,
            modifier = Modifier.width(120.dp).align(Alignment.Center).graphicsLayer { rotationZ = 270f },
            color = Color(0xFFFFF0C8), fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        PlacementMenu(
            book = book,
            savingBookUri = savingBookUri,
            modifier = Modifier.align(Alignment.TopEnd),
            onBookAction = onAction,
            onMove = onMove,
            onToggleOrientation = onToggleOrientation,
            onRemove = onRemoveFromShelf
        )
    }
}

@Composable
private fun StackedBook(
    book: LocalBook,
    savingBookUri: String?,
    onAction: (LibraryBookAction) -> Unit,
    onMove: (shelfDelta: Int, orderDelta: Int) -> Unit,
    onToggleOrientation: () -> Unit,
    onRemoveFromShelf: () -> Unit
) {
    val colors = spinePalette(book.id)
    Row(
        Modifier.width(164.dp).height(76.dp).shadow(6.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(colors))
            .clickable(onClickLabel = book.title, onClick = { onAction(LibraryBookAction.OPEN) })
            .semantics { role = Role.Button; contentDescription = book.title },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(52.dp).padding(4.dp).height(68.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)) {
            when (book.format) {
                "PDF" -> LocalPdfCover(book.uri, book.offlineCopyUri, Modifier.fillMaxWidth().height(68.dp))
                "EPUB" -> LocalEpubCover(book.uri, book.offlineCopyUri, Modifier.fillMaxWidth().height(68.dp))
                else -> Text(book.format, modifier = Modifier.align(Alignment.Center), fontSize = 9.sp, color = Color.DarkGray)
            }
        }
        Text(
            book.title,
            modifier = Modifier.weight(1f).padding(start = 3.dp, end = 25.dp),
            color = Color(0xFFFFF0C8), fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis
        )
        PlacementMenu(
            book = book,
            savingBookUri = savingBookUri,
            modifier = Modifier.align(Alignment.Top).padding(end = 2.dp),
            onBookAction = onAction,
            onMove = onMove,
            onToggleOrientation = onToggleOrientation,
            onRemove = onRemoveFromShelf
        )
    }
}

@Composable
private fun PlacementMenu(
    book: LocalBook,
    savingBookUri: String?,
    modifier: Modifier = Modifier,
    onBookAction: (LibraryBookAction) -> Unit,
    onMove: (Int, Int) -> Unit,
    onToggleOrientation: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.accessibility_book_options, book.title), tint = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_left)) }, leadingIcon = { Icon(Icons.Default.ArrowBack, null) }, onClick = { expanded = false; onMove(0, -1) })
            DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_right)) }, leadingIcon = { Icon(Icons.Default.ArrowForward, null) }, onClick = { expanded = false; onMove(0, 1) })
            DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_up)) }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { expanded = false; onMove(-1, 0) })
            DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_down)) }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { expanded = false; onMove(1, 0) })
            DropdownMenuItem(text = { Text(stringResource(R.string.shelf_orientation_change)) }, leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }, onClick = { expanded = false; onToggleOrientation() })
            DropdownMenuItem(text = { Text(stringResource(R.string.shelf_remove_book)) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { expanded = false; onRemove() })
            androidx.compose.material3.HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(if (book.isFavorite) R.string.book_remove_favorite else R.string.book_add_favorite)) },
                onClick = { expanded = false; onBookAction(LibraryBookAction.TOGGLE_FAVORITE) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (book.offlineCopyUri != null) R.string.book_remove_offline_copy else R.string.book_save_offline)) },
                enabled = savingBookUri != book.uri.toString(),
                onClick = {
                    expanded = false
                    onBookAction(if (book.offlineCopyUri != null) LibraryBookAction.REMOVE_OFFLINE_COPY else LibraryBookAction.SAVE_OFFLINE_COPY)
                }
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.book_export_annotations)) }, onClick = { expanded = false; onBookAction(LibraryBookAction.EXPORT_ANNOTATIONS) })
            DropdownMenuItem(text = { Text(stringResource(R.string.book_organize_collection)) }, onClick = { expanded = false; onBookAction(LibraryBookAction.ORGANIZE) })
            DropdownMenuItem(text = { Text(stringResource(R.string.library_edit_title)) }, onClick = { expanded = false; onBookAction(LibraryBookAction.EDIT_TITLE) })
            DropdownMenuItem(text = { Text(stringResource(R.string.library_remove_title)) }, onClick = { expanded = false; onBookAction(LibraryBookAction.REMOVE) })
        }
    }
}

@Composable
private fun ShelfDecorationCard(
    decoration: ShelfDecoration,
    onMove: (shelfDelta: Int, orderDelta: Int) -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit
) {
    val type = decoration.type
    val decorationLabel = stringResource(R.string.accessibility_bookshelf_decoration_named, stringResource(type.labelRes))
    Box(
        Modifier.width(76.dp).height(116.dp)
            .semantics { contentDescription = decorationLabel },
        contentAlignment = Alignment.BottomCenter
    ) {
        ShelfDecorationArt(type, Modifier.fillMaxWidth().height(102.dp).padding(horizontal = 2.dp))
        var expanded by remember { mutableStateOf(false) }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = decorationLabel, tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_change_decoration)) }, onClick = { expanded = false; onReplace() })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_left)) }, onClick = { expanded = false; onMove(0, -1) })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_right)) }, onClick = { expanded = false; onMove(0, 1) })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_up)) }, onClick = { expanded = false; onMove(-1, 0) })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_down)) }, onClick = { expanded = false; onMove(1, 0) })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_remove_decoration)) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { expanded = false; onRemove() })
            }
        }
    }
}

@Composable
internal fun BookshelfCustomizerDialog(
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    coverRows: List<List<LocalBook>>,
    shelves: List<ShelfDisplay>,
    booksById: Map<String, LocalBook>,
    savingBookUri: String?,
    onBookAction: (LocalBook, LibraryBookAction) -> Unit,
    onMoveBook: (String, Int, Int) -> Unit,
    onToggleOrientation: (String) -> Unit,
    onRemoveBook: (String) -> Unit,
    onMoveDecoration: (String, Int, Int) -> Unit,
    onReplaceDecoration: (String, ShelfDecorationType) -> Unit,
    onRemoveDecoration: (String) -> Unit,
    replacementDecorationId: String?,
    onAddDecoration: (ShelfDecorationType) -> Unit,
    onAddBooks: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit
) {
    var selectedCategoryName by rememberSaveable { mutableStateOf(ShelfDecorationCategory.ALL.name) }
    var selectedDecorationName by rememberSaveable { mutableStateOf<String?>(null) }
    var decorationToReplaceId by rememberSaveable(replacementDecorationId) { mutableStateOf(replacementDecorationId) }
    val selectedCategory = runCatching { ShelfDecorationCategory.valueOf(selectedCategoryName) }
        .getOrDefault(ShelfDecorationCategory.ALL)
    val galleryTypes = ShelfDecorationType.entries.filterNot { it == ShelfDecorationType.SCULPTURE }
    val availableDecorations = galleryTypes.filter { it.belongsTo(selectedCategory) }
    val selectedDecoration = selectedDecorationName?.let { name ->
        galleryTypes.firstOrNull { it.name == name }
    }
    val darkTheme = isSystemInDarkTheme()
    val dialogColors = MaterialTheme.colorScheme
    val dialogBackground = if (darkTheme) dialogColors.surface else Color(0xFFFFF8EF)
    val dialogInk = if (darkTheme) dialogColors.onSurface else Color(0xFF073D39)
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp - 48).coerceIn(420, 860).dp

    Dialog(
        onDismissRequest = onSave,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.97f).heightIn(max = maxDialogHeight),
            shape = RoundedCornerShape(28.dp),
            color = dialogBackground,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(onClick = onSave, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close), tint = Color(0xFF064F48))
                    }
                    Image(
                        painter = painterResource(R.drawable.leepe_logo),
                        contentDescription = stringResource(R.string.accessibility_leepe_logo),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        stringResource(R.string.shelf_customizer_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = dialogInk,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB81C), contentColor = Color(0xFF073D39)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shelf_save))
                    }
                }

                LibraryViewModeSelector(viewMode = viewMode, onModeChange = onViewModeChange, modifier = Modifier.fillMaxWidth())

                Surface(color = if (darkTheme) dialogColors.surfaceVariant else Color(0xFFFFF0D9), shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = if (darkTheme) dialogColors.primary else Color(0xFFDB8A00))
                        Text(
                            stringResource(R.string.shelf_customizer_hint),
                            color = if (darkTheme) dialogColors.onSurfaceVariant else Color(0xFF443D35),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (viewMode == LibraryViewMode.MIXED_BOOKSHELF) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onAddBooks) { Text(stringResource(R.string.shelf_add_books)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onReset) { Text(stringResource(R.string.shelf_reset)) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        shelves.forEach { shelf ->
                            BookshelfRow(
                                shelf = shelf,
                                booksById = booksById,
                                savingBookUri = savingBookUri,
                                onBookAction = onBookAction,
                                onMoveBook = onMoveBook,
                                onToggleOrientation = onToggleOrientation,
                                onRemoveBook = onRemoveBook,
                                onMoveDecoration = onMoveDecoration,
                                onReplaceDecoration = { id -> decorationToReplaceId = id },
                                onRemoveDecoration = onRemoveDecoration,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Text(stringResource(R.string.shelf_gallery_heading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = dialogInk)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ShelfDecorationCategory.entries, key = ShelfDecorationCategory::name) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategoryName = category.name },
                                label = { Text(stringResource(category.labelRes), maxLines = 1) }
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(availableDecorations, key = ShelfDecorationType::name) { type ->
                            val selected = selectedDecoration == type
                            Surface(
                                modifier = Modifier.width(132.dp).height(154.dp)
                                    .semantics {
                                        role = Role.RadioButton
                                        contentDescription = stringResource(type.labelRes)
                                        selected = selected
                                    }
                                    .clickable { selectedDecorationName = type.name },
                                shape = RoundedCornerShape(20.dp),
                                color = when {
                                    selected && darkTheme -> dialogColors.primaryContainer
                                    selected -> Color(0xFFE5F4EE)
                                    darkTheme -> dialogColors.surface
                                    else -> Color(0xFFFFFBF6)
                                },
                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) dialogColors.primary else dialogColors.outlineVariant),
                                shadowElevation = if (selected) 5.dp else 2.dp
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    ShelfDecorationArt(type, Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp))
                                    Text(stringResource(type.labelRes), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = if (darkTheme && selected) dialogColors.onPrimaryContainer else dialogColors.onSurface)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            selectedDecoration?.let { type ->
                                val replacementId = decorationToReplaceId
                                if (replacementId == null) onAddDecoration(type) else onReplaceDecoration(replacementId, type)
                            }
                            decorationToReplaceId = null
                            selectedDecorationName = null
                        },
                        enabled = selectedDecoration != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695F), contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (decorationToReplaceId == null) R.string.shelf_add_selected else R.string.shelf_replace_selected))
                    }
                } else {
                    Text(stringResource(R.string.shelf_covers_hint), color = if (darkTheme) dialogColors.onSurfaceVariant else Color(0xFF443D35), style = MaterialTheme.typography.bodyMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        coverRows.forEach { row ->
                            CoverShelfRow(
                                books = row,
                                savingBookUri = savingBookUri,
                                onBookAction = onBookAction
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfDecorationArt(type: ShelfDecorationType, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(type.drawableRes),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

@Composable
internal fun BookshelfHiddenBooksDialog(
    books: List<LocalBook>,
    onAdd: (LocalBook) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shelf_add_books_title)) },
        text = {
            if (books.isEmpty()) {
                Text(stringResource(R.string.shelf_no_hidden_books))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    books.forEach { book ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onAdd(book) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(book.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { onAdd(book) }) { Text(stringResource(R.string.shelf_add_book)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

private fun spinePalette(seed: String): List<Color> {
    val palettes = listOf(
        Color(0xFF155B51), Color(0xFF173E56), Color(0xFF9A4A3D), Color(0xFFC58C4B),
        Color(0xFF536349), Color(0xFF6B4C62), Color(0xFF285B75), Color(0xFF876B35)
    )
    val color = palettes[(seed.hashCode().toLong() and 0x7fffffffL).rem(palettes.size).toInt()]
    return listOf(color.copy(red = (color.red * .74f).coerceAtLeast(0f)), color, color.copy(red = (color.red * 1.13f).coerceAtMost(1f)))
}
