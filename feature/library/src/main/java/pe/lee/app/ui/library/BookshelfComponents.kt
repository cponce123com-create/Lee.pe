package pe.lee.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onRemove: () -> Unit
) {
    val type = decoration.type
    val decorationLabel = stringResource(R.string.accessibility_bookshelf_decoration_named, stringResource(type.labelRes))
    val tint = when (type) {
        ShelfDecorationType.PLANT -> Color(0xFF49734B)
        ShelfDecorationType.VASE -> Color(0xFFB56D62)
        ShelfDecorationType.LAMP -> Color(0xFFD9A52B)
        ShelfDecorationType.ORB -> Color(0xFF6D81A7)
        ShelfDecorationType.FIGURE -> Color(0xFF8A6041)
        ShelfDecorationType.BOOKEND -> Color(0xFF2E6F68)
        ShelfDecorationType.DIVIDER -> Color(0xFF8B6743)
        ShelfDecorationType.SCULPTURE -> Color(0xFF986C9F)
    }
    Box(Modifier.width(54.dp).height(104.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            ShelfDecorationArt(type, tint, Modifier.size(48.dp, 68.dp))
            Box(Modifier.width(43.dp).height(32.dp).shadow(4.dp, RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)).background(Brush.verticalGradient(listOf(tint.copy(alpha = .72f), tint)), RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)))
        }
        var expanded by remember { mutableStateOf(false) }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = decorationLabel, tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
internal fun ShelfDecorationGallery(onSelect: (ShelfDecorationType) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shelf_decoration_gallery_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShelfDecorationType.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { type ->
                            val label = stringResource(type.labelRes)
                            Surface(
                                modifier = Modifier.weight(1f).height(94.dp)
                                    .semantics { role = Role.Button; contentDescription = label }
                                    .clickable { onSelect(type) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    ShelfDecorationArt(type, MaterialTheme.colorScheme.primary, Modifier.size(46.dp, 52.dp))
                                    Text(stringResource(type.labelRes), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun ShelfDecorationArt(type: ShelfDecorationType, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val mid = w / 2f
        val shadowColor = Color(0x33000000)
        when (type) {
            ShelfDecorationType.ORB -> {
                drawCircle(shadowColor, w * .34f, Offset(mid + 2.dp.toPx(), h * .68f))
                drawCircle(Brush.radialGradient(listOf(Color.White, color.copy(alpha = .92f), color.copy(alpha = .72f)), Offset(mid * .68f, h * .35f), w * .68f), w * .34f, Offset(mid, h * .57f))
                drawCircle(Color.White.copy(alpha = .8f), w * .055f, Offset(mid * .64f, h * .42f))
            }
            ShelfDecorationType.PLANT -> {
                val potTop = h * .72f
                drawLine(Color(0xFF47734B), Offset(mid, potTop), Offset(mid, h * .18f), 2.4.dp.toPx())
                listOf(-1f, 1f).forEachIndexed { index, side ->
                    val y = h * (.35f + index * .12f)
                    rotate(side * 32f, Offset(mid, y)) {
                        drawOval(Brush.linearGradient(listOf(Color(0xFF8EC37B), color)), topLeft = Offset(mid - w * .2f, y - h * .1f), size = Size(w * .4f, h * .2f))
                    }
                }
                val pot = Path().apply { moveTo(w * .22f, potTop); lineTo(w * .78f, potTop); lineTo(w * .68f, h * .96f); lineTo(w * .32f, h * .96f); close() }
                drawPath(pot, Brush.verticalGradient(listOf(Color(0xFFD8B48B), Color(0xFF9B6745)), potTop, h))
                drawOval(Color(0xFF583A2A), topLeft = Offset(w * .22f, potTop - h * .035f), size = Size(w * .56f, h * .08f))
            }
            ShelfDecorationType.VASE -> {
                val vase = Path().apply {
                    moveTo(w * .42f, h * .42f); lineTo(w * .42f, h * .5f)
                    cubicTo(w * .4f, h * .58f, w * .2f, h * .62f, w * .24f, h * .83f)
                    cubicTo(w * .27f, h * .99f, w * .73f, h * .99f, w * .76f, h * .83f)
                    cubicTo(w * .8f, h * .62f, w * .6f, h * .58f, w * .58f, h * .5f)
                    lineTo(w * .58f, h * .42f); close()
                }
                drawPath(vase, Brush.horizontalGradient(listOf(color.copy(alpha = .65f), Color.White.copy(alpha = .65f), color), 0f, w))
                drawLine(Color.White.copy(alpha = .7f), Offset(w * .43f, h * .45f), Offset(w * .57f, h * .45f), 2.dp.toPx())
            }
            ShelfDecorationType.LAMP -> {
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFE8A1), Color(0xFFD29A2A))), Offset(w * .17f, h * .21f), Size(w * .66f, h * .32f), androidx.compose.ui.geometry.CornerRadius(w * .14f))
                drawLine(color, Offset(mid, h * .52f), Offset(mid, h * .85f), 3.dp.toPx())
                drawRoundRect(Brush.horizontalGradient(listOf(color, Color.White.copy(alpha = .7f), color)), Offset(w * .3f, h * .84f), Size(w * .4f, h * .08f), androidx.compose.ui.geometry.CornerRadius(h * .04f))
                drawCircle(Color(0xFFFFD96A), w * .08f, Offset(mid, h * .59f))
            }
            ShelfDecorationType.BOOKEND, ShelfDecorationType.DIVIDER -> {
                val path = Path().apply {
                    moveTo(w * .2f, h * .92f); lineTo(w * .2f, h * .2f); lineTo(w * .62f, h * .2f)
                    lineTo(w * .62f, h * .38f); lineTo(w * .4f, h * .38f); lineTo(w * .4f, h * .92f); close()
                }
                drawPath(path, Brush.horizontalGradient(listOf(color.copy(alpha = .7f), color, Color.White.copy(alpha = .45f))))
                drawPath(path, color.copy(alpha = .75f), style = Stroke(1.dp.toPx()))
                if (type == ShelfDecorationType.DIVIDER) {
                    drawLine(Color.White.copy(alpha = .6f), Offset(w * .72f, h * .2f), Offset(w * .72f, h * .91f), 2.dp.toPx())
                }
            }
            ShelfDecorationType.FIGURE -> {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .85f), color)), w * .15f, Offset(mid, h * .25f))
                drawRoundRect(Brush.horizontalGradient(listOf(color.copy(alpha = .75f), color, Color.White.copy(alpha = .6f))), Offset(w * .33f, h * .42f), Size(w * .34f, h * .42f), androidx.compose.ui.geometry.CornerRadius(w * .15f))
                drawLine(color, Offset(w * .42f, h * .82f), Offset(w * .31f, h * .98f), 3.dp.toPx())
                drawLine(color, Offset(w * .58f, h * .82f), Offset(w * .69f, h * .98f), 3.dp.toPx())
            }
            ShelfDecorationType.SCULPTURE -> {
                val path = Path().apply {
                    moveTo(mid, h * .08f); lineTo(w * .85f, h * .55f); lineTo(mid, h * .95f); lineTo(w * .15f, h * .55f); close()
                }
                drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha = .75f), color, color.copy(alpha = .65f))))
                drawPath(path, color.copy(alpha = .8f), style = Stroke(1.dp.toPx()))
                drawLine(Color.White.copy(alpha = .7f), Offset(mid, h * .08f), Offset(mid, h * .95f), 1.dp.toPx())
            }
        }
    }
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
