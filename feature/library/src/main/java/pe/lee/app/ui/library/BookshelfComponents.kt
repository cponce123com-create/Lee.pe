package pe.lee.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

internal fun buildShelfDisplay(orderedBooks: List<LocalBook>, layout: BookshelfLayout, customOrder: Boolean): List<ShelfDisplay> {
    val booksById = orderedBooks.associateBy { it.id }
    if (customOrder) {
        return layout.objectsByShelf().mapNotNull { (shelf, items) ->
            val objects = items.filter { item ->
                when (item) {
                    is ShelfObject.Book -> item.placement.bookId in booksById
                    is ShelfObject.Decor -> true
                }
            }
            objects.takeIf { it.isNotEmpty() }?.let { ShelfDisplay(shelf, it) }
        }
    }

    val placements = layout.books.associateBy { it.bookId }
    val decorationsByShelf = layout.decorations.groupBy { it.shelf }
    return orderedBooks.chunked(5).mapIndexed { shelf, books ->
        val bookObjects = books.mapIndexed { index, book ->
            ShelfObject.Book(
                placements[book.id]?.copy(shelf = shelf, order = index * 2)
                    ?: BookshelfBookPlacement(book.id, shelf, index * 2, BookOrientation.UPRIGHT),
            )
        }
        val decorationObjects = decorationsByShelf[shelf].orEmpty().map { decor ->
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
    modifier: Modifier = Modifier,
) {
    val wood = Brush.verticalGradient(listOf(Color(0xFFE6B878), Color(0xFFB8793E), Color(0xFF78451F)))
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(min = 154.dp).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.Bottom,
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
                                onRemoveFromShelf = { onRemoveBook(book.id) },
                            )
                        } else {
                            StackedBook(
                                book = book,
                                savingBookUri = savingBookUri,
                                onAction = { onBookAction(book, it) },
                                onMove = { shelfDelta, orderDelta -> onMoveBook(book.id, shelfDelta, orderDelta) },
                                onToggleOrientation = { onToggleOrientation(book.id) },
                                onRemoveFromShelf = { onRemoveBook(book.id) },
                            )
                        }
                    }
                    is ShelfObject.Decor -> ShelfDecorationCard(
                        decoration = item.decoration,
                        onMove = { shelfDelta, orderDelta -> onMoveDecoration(item.decoration.id, shelfDelta, orderDelta) },
                        onReplace = { onReplaceDecoration(item.decoration.id) },
                        onRemove = { onRemoveDecoration(item.decoration.id) },
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 1.dp).height(12.dp)
                .shadow(5.dp, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
                .background(wood),
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
    onRemoveFromShelf: () -> Unit,
) {
    val colors = spinePalette(book.id)
    Box(
        Modifier.width(54.dp).height(134.dp).shadow(7.dp, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .background(Brush.horizontalGradient(colors))
            .clickable(onClickLabel = book.title, onClick = { onAction(LibraryBookAction.OPEN) })
            .semantics {
                role = Role.Button
                contentDescription = book.title
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter).background(Color(0x66FFF0C7)))
        Text(
            text = book.title,
            modifier = Modifier.width(120.dp).align(Alignment.Center).graphicsLayer { rotationZ = 270f },
            color = Color(0xFFFFF0C8),
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        PlacementMenu(
            book = book,
            savingBookUri = savingBookUri,
            modifier = Modifier.align(Alignment.TopEnd),
            onBookAction = onAction,
            onMove = onMove,
            onToggleOrientation = onToggleOrientation,
            onRemove = onRemoveFromShelf,
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
    onRemoveFromShelf: () -> Unit,
) {
    val colors = spinePalette(book.id)
    Row(
        Modifier.width(164.dp).height(76.dp).shadow(6.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(colors))
            .clickable(onClickLabel = book.title, onClick = { onAction(LibraryBookAction.OPEN) })
            .semantics {
                role = Role.Button
                contentDescription = book.title
            },
        verticalAlignment = Alignment.CenterVertically,
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
            color = Color(0xFFFFF0C8),
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        PlacementMenu(
            book = book,
            savingBookUri = savingBookUri,
            modifier = Modifier.align(Alignment.Top).padding(end = 2.dp),
            onBookAction = onAction,
            onMove = onMove,
            onToggleOrientation = onToggleOrientation,
            onRemove = onRemoveFromShelf,
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
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.accessibility_book_options, book.title),
                tint = Color.White,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = {
                Text(stringResource(R.string.shelf_move_left))
            }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }, onClick = {
                expanded =
                    false
                onMove(0, -1)
            })
            DropdownMenuItem(text = {
                Text(stringResource(R.string.shelf_move_right))
            }, leadingIcon = { Icon(Icons.Default.ArrowForward, null) }, onClick = {
                expanded =
                    false
                onMove(0, 1)
            })
            DropdownMenuItem(text = {
                Text(stringResource(R.string.shelf_move_up))
            }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = {
                expanded =
                    false
                onMove(-1, 0)
            })
            DropdownMenuItem(text = {
                Text(stringResource(R.string.shelf_move_down))
            }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = {
                expanded =
                    false
                onMove(1, 0)
            })
            DropdownMenuItem(text = {
                Text(stringResource(R.string.shelf_orientation_change))
            }, leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }, onClick = {
                expanded =
                    false
                onToggleOrientation()
            })
            DropdownMenuItem(text = {
                Text(stringResource(R.string.shelf_remove_book))
            }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = {
                expanded =
                    false
                onRemove()
            })
            androidx.compose.material3.HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(if (book.isFavorite) R.string.book_remove_favorite else R.string.book_add_favorite)) },
                onClick = {
                    expanded = false
                    onBookAction(LibraryBookAction.TOGGLE_FAVORITE)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (book.offlineCopyUri !=
                                null
                            ) {
                                R.string.book_remove_offline_copy
                            } else {
                                R.string.book_save_offline
                            },
                        ),
                    )
                },
                enabled = savingBookUri != book.uri.toString(),
                onClick = {
                    expanded = false
                    onBookAction(
                        if (book.offlineCopyUri !=
                            null
                        ) {
                            LibraryBookAction.REMOVE_OFFLINE_COPY
                        } else {
                            LibraryBookAction.SAVE_OFFLINE_COPY
                        },
                    )
                },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.book_export_annotations)) }, onClick = {
                expanded = false
                onBookAction(LibraryBookAction.EXPORT_ANNOTATIONS)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.book_organize_collection)) }, onClick = {
                expanded = false
                onBookAction(LibraryBookAction.ORGANIZE)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.library_edit_title)) }, onClick = {
                expanded = false
                onBookAction(LibraryBookAction.EDIT_TITLE)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.library_remove_title)) }, onClick = {
                expanded = false
                onBookAction(LibraryBookAction.REMOVE)
            })
        }
    }
}

@Composable
private fun ShelfDecorationCard(
    decoration: ShelfDecoration,
    onMove: (shelfDelta: Int, orderDelta: Int) -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    val type = decoration.type
    val decorationLabel = stringResource(R.string.accessibility_bookshelf_decoration_named, stringResource(type.labelRes))
    Box(
        Modifier.width(76.dp).height(116.dp)
            .semantics { contentDescription = decorationLabel },
        contentAlignment = Alignment.BottomCenter,
    ) {
        ShelfDecorationArt(type, Modifier.fillMaxWidth().height(102.dp).padding(horizontal = 2.dp))
        var expanded by remember { mutableStateOf(false) }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = decorationLabel, tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_change_decoration)) }, onClick = {
                    expanded = false
                    onReplace()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_left)) }, onClick = {
                    expanded = false
                    onMove(0, -1)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_right)) }, onClick = {
                    expanded = false
                    onMove(0, 1)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_up)) }, onClick = {
                    expanded = false
                    onMove(-1, 0)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.shelf_move_down)) }, onClick = {
                    expanded = false
                    onMove(1, 0)
                })
                DropdownMenuItem(text = {
                    Text(stringResource(R.string.shelf_remove_decoration))
                }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = {
                    expanded =
                        false
                    onRemove()
                })
            }
        }
    }
}

private fun spinePalette(seed: String): List<Color> {
    val palettes = listOf(
        Color(0xFF155B51),
        Color(0xFF173E56),
        Color(0xFF9A4A3D),
        Color(0xFFC58C4B),
        Color(0xFF536349),
        Color(0xFF6B4C62),
        Color(0xFF285B75),
        Color(0xFF876B35),
    )
    val color = palettes[(seed.hashCode().toLong() and 0x7fffffffL).rem(palettes.size).toInt()]
    return listOf(color.copy(red = (color.red * .74f).coerceAtLeast(0f)), color, color.copy(red = (color.red * 1.13f).coerceAtMost(1f)))
}
