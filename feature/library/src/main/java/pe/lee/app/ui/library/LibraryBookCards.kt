package pe.lee.app.ui.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.feature.library.R
import kotlin.math.absoluteValue

internal enum class LibraryBookAction {
    OPEN, REMOVE, SAVE_OFFLINE_COPY, REMOVE_OFFLINE_COPY, EXPORT_ANNOTATIONS,
    TOGGLE_FAVORITE, EDIT_TITLE, ORGANIZE
}

@Composable
internal fun LibraryStatisticRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EmptyLibraryCard(hasBooks: Boolean, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF6))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                .size(68.dp)
                    .background(Color(0xFF075B50), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                stringResource(if (hasBooks) R.string.library_no_matches else R.string.library_empty_welcome),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(if (hasBooks) R.string.library_try_another_search else R.string.library_empty_import_prompt),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasBooks) {
                Button(onClick = onImport, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.library_choose_files))
                }
            }
        }
    }
}

@Composable
internal fun BookCard(
    book: LocalBook,
    savingBookUri: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onAction: (LibraryBookAction) -> Unit
) {
    val (icon, colors) = when (book.format) {
        "PDF" -> Icons.Default.PictureAsPdf to listOf(Color(0xFF5C2A70), Color(0xFF1F4C70))
        "Audio" -> Icons.Default.Headphones to listOf(Color(0xFF145D67), Color(0xFF26396F))
        else -> Icons.AutoMirrored.Filled.MenuBook to listOf(Color(0xFF603680), Color(0xFF204D74))
    }

    Card(
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.accessibility_open_book, book.title),
            onClick = { onAction(LibraryBookAction.OPEN) }
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF6))
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 86.dp)
                        .background(Brush.linearGradient(colors), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when (book.format) {
                        "PDF" -> LocalPdfCover(book.uri, book.offlineCopyUri, Modifier.fillMaxSize())
                        "EPUB" -> LocalEpubCover(book.uri, book.offlineCopyUri, Modifier.fillMaxSize())
                        else -> Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        book.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            book.format == "Audio" -> stringResource(R.string.book_status_audiobook)
                            book.readProgress >= 1f -> stringResource(R.string.book_status_finished)
                            book.readProgress > 0f -> stringResource(R.string.book_progress_percent, (book.readProgress * 100).toInt())
                            else -> stringResource(R.string.book_status_to_read)
                        }.let { status -> stringResource(R.string.book_status_with_format, status, book.format) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (book.format != "Audio" && book.readProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { book.readProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                BookActions(
                    book = book,
                    savingBookUri = savingBookUri,
                    onAction = onAction
                )
            }
        } else {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(Brush.linearGradient(colors), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (book.format) {
                    "PDF" -> LocalPdfCover(
                        originalUri = book.uri,
                        offlineCopyUri = book.offlineCopyUri,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )
                    "EPUB" -> LocalEpubCover(
                        originalUri = book.uri,
                        offlineCopyUri = book.offlineCopyUri,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    )
                    else -> Icon(
                        icon, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(42.dp)
                    )
                }
                if (book.isFavorite) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = stringResource(R.string.accessibility_favorite_book),
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    )
                }
                BookActions(
                    book = book,
                    savingBookUri = savingBookUri,
                    modifier = Modifier.align(Alignment.TopEnd),
                    iconTint = Color.White,
                    onAction = onAction
                )
                Text(
                    book.format,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .background(Color(0x55000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                book.title,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF25211D),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.format != "Audio" && book.readProgress > 0f) {
                LinearProgressIndicator(
                    progress = { book.readProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            val readingStatus = when {
                book.format == "Audio" -> stringResource(R.string.book_status_audiobook)
                book.readProgress >= 1f -> stringResource(R.string.book_status_finished)
                book.readProgress > 0f -> stringResource(R.string.book_progress_percent, (book.readProgress.coerceIn(0f, 1f) * 100).toInt())
                else -> stringResource(R.string.book_status_not_started)
            }
            Text(
                readingStatus,
                color = Color(0xFF6B625A),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = if (book.offlineCopyUri == null) 4.dp else 2.dp)
            )
            if (book.offlineCopyUri != null) {
                Text(
                    stringResource(R.string.book_available_offline),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun BookActions(
    book: LocalBook,
    savingBookUri: String?,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onAction: (LibraryBookAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.accessibility_book_options, book.title),
                            tint = iconTint
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (book.isFavorite) R.string.book_remove_favorite else R.string.book_add_favorite))
                            },
                            onClick = {
                                expanded = false
                                onAction(LibraryBookAction.TOGGLE_FAVORITE)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        when {
                                            book.offlineCopyUri != null -> R.string.book_remove_offline_copy
                                            savingBookUri == book.uri.toString() -> R.string.book_saving_copy
                                            else -> R.string.book_save_offline
                                        }
                                    )
                                )
                            },
                            enabled = savingBookUri != book.uri.toString(),
                            onClick = {
                                expanded = false
                                onAction(if (book.offlineCopyUri != null) LibraryBookAction.REMOVE_OFFLINE_COPY else LibraryBookAction.SAVE_OFFLINE_COPY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.book_export_annotations)) },
                            onClick = {
                                expanded = false
                                onAction(LibraryBookAction.EXPORT_ANNOTATIONS)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.book_organize_collection)) },
                            onClick = {
                                expanded = false
                                onAction(LibraryBookAction.ORGANIZE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_edit_title)) },
                            onClick = {
                                expanded = false
                                onAction(LibraryBookAction.EDIT_TITLE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_remove_title)) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onAction(LibraryBookAction.REMOVE)
                            }
                        )
                    }
                }

}

/** Mixed layout inspired by a physical shelf: every book remains an actionable book card. */
@Composable
internal fun BookshelfRow(
    books: List<LocalBook>,
    savingBookUri: String?,
    adornment: BookshelfAdornment,
    onBookAction: (LocalBook, LibraryBookAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val wood = Brush.verticalGradient(listOf(Color(0xFFB8793E), Color(0xFF75411F), Color(0xFF4A2A18)))
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(148.dp).padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            books.forEachIndexed { index, book ->
                val palette = shelfPalette(book.id)
                val vertical = index % 4 != 2
                if (vertical) {
                    Box(
                        modifier = Modifier.weight(if (index % 3 == 0) 1.12f else 0.9f).height((116 + (book.id.hashCode().absoluteValue % 26)).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Brush.horizontalGradient(listOf(palette.first, palette.second)))
                            .clickable(onClickLabel = book.title, onClick = { onBookAction(book, LibraryBookAction.OPEN) })
                            .semantics { role = Role.Button; contentDescription = book.title },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp)) {
                            Text(
                                text = book.title,
                                modifier = Modifier.align(Alignment.Center).size(width = 122.dp, height = 22.dp).graphicsLayer { rotationZ = 270f },
                                color = Color(0xFFFFE6A5),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Box(Modifier.align(Alignment.TopEnd)) {
                                BookActions(book, savingBookUri, iconTint = Color(0xFFFFF5DB), onAction = { onBookAction(book, it) })
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1.18f).padding(bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(45.dp).clip(RoundedCornerShape(5.dp))
                                .background(Brush.verticalGradient(listOf(palette.second, palette.first)))
                                .clickable(onClickLabel = book.title, onClick = { onBookAction(book, LibraryBookAction.OPEN) })
                                .semantics { role = Role.Button; contentDescription = book.title },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(book.title, color = Color(0xFFFFE7AA), fontWeight = FontWeight.SemiBold, fontSize = 9.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp))
                            Box(Modifier.align(Alignment.TopEnd)) {
                                BookActions(book, savingBookUri, iconTint = Color(0xFFFFF5DB), onAction = { onBookAction(book, it) })
                            }
                        }
                    }
                }
            }
            if (adornment != BookshelfAdornment.NONE) {
                ShelfDecoration(adornment = adornment, modifier = Modifier.weight(0.55f).height(72.dp))
            }
        }
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(wood)
        )
    }
}

@Composable
private fun ShelfDecoration(adornment: BookshelfAdornment, modifier: Modifier = Modifier) {
    val symbol = when (adornment) {
        BookshelfAdornment.FLOWERS -> "✿"
        BookshelfAdornment.PLANTS -> "♣"
        BookshelfAdornment.FIGURES -> "●"
        BookshelfAdornment.NONE -> ""
    }
    val color = when (adornment) {
        BookshelfAdornment.FLOWERS -> Color(0xFF8A673E)
        BookshelfAdornment.PLANTS -> Color(0xFF547342)
        BookshelfAdornment.FIGURES -> Color(0xFFB68E55)
        BookshelfAdornment.NONE -> Color.Transparent
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Text(symbol, fontSize = 30.sp, color = color, textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth(0.7f).height(22.dp).clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp)).background(Color(0xFFD6C2A1)))
    }
}

private fun shelfPalette(seed: String): Pair<Color, Color> {
    val palettes = listOf(
        Color(0xFF155B51) to Color(0xFF0D3F3B), Color(0xFF173E56) to Color(0xFF102C40),
        Color(0xFF9A4A3D) to Color(0xFF6F302B), Color(0xFFC58C4B) to Color(0xFF8D5A31),
        Color(0xFF536349) to Color(0xFF34452F), Color(0xFF6B4C62) to Color(0xFF493346)
    )
    val index = (seed.hashCode().toLong() and 0x7fffffffL).rem(palettes.size).toInt()
    return palettes[index]
}
