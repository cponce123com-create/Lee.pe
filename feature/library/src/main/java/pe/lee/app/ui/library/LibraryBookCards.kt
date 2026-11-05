package pe.lee.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.feature.library.R

internal enum class LibraryBookAction {
    OPEN,
    REMOVE,
    SAVE_OFFLINE_COPY,
    REMOVE_OFFLINE_COPY,
    EXPORT_ANNOTATIONS,
    TOGGLE_FAVORITE,
    EDIT_TITLE,
    ORGANIZE,
}

@Composable
internal fun LibraryStatisticRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EmptyLibraryCard(hasBooks: Boolean, onImport: () -> Unit) {
    val cardSurface = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color(0xFFFFFBF6)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardSurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(Color(0xFF075B50), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                stringResource(if (hasBooks) R.string.library_no_matches else R.string.library_empty_welcome),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(if (hasBooks) R.string.library_try_another_search else R.string.library_empty_import_prompt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun CoverShelfRow(
    books: List<LocalBook>,
    savingBookUri: String?,
    onBookAction: (LocalBook, LibraryBookAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shelfWood = Brush.verticalGradient(listOf(Color(0xFFF0D4A5), Color(0xFFD09A5A), Color(0xFF9A5C2C)))
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            books.forEach { book ->
                BookCard(
                    book = book,
                    savingBookUri = savingBookUri,
                    modifier = Modifier.weight(1f),
                    onAction = { onBookAction(book, it) },
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(13.dp).shadow(6.dp, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp)).background(shelfWood),
        )
    }
}

@Composable
internal fun BookCard(
    book: LocalBook,
    savingBookUri: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onAction: (LibraryBookAction) -> Unit,
) {
    val cardSurface = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color(0xFFFFFBF6)
    val cardInk = MaterialTheme.colorScheme.onSurface
    val mutedInk = MaterialTheme.colorScheme.onSurfaceVariant
    val (icon, colors) = when (book.format) {
        "PDF" -> Icons.Default.PictureAsPdf to listOf(Color(0xFF5C2A70), Color(0xFF1F4C70))
        "Audio" -> Icons.Default.Headphones to listOf(Color(0xFF145D67), Color(0xFF26396F))
        else -> Icons.AutoMirrored.Filled.MenuBook to listOf(Color(0xFF603680), Color(0xFF204D74))
    }

    Card(
        modifier = modifier.clickable(
            onClickLabel = stringResource(R.string.accessibility_open_book, book.title),
            onClick = { onAction(LibraryBookAction.OPEN) },
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardSurface),
    ) {
        if (compact) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 86.dp)
                        .background(Brush.linearGradient(colors), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
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
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            book.format == "Audio" -> stringResource(R.string.book_status_audiobook)
                            book.readProgress >= 1f -> stringResource(R.string.book_status_finished)
                            book.readProgress > 0f -> stringResource(R.string.book_progress_percent, (book.readProgress * 100).toInt())
                            else -> stringResource(R.string.book_status_to_read)
                        }.let { status -> stringResource(R.string.book_status_with_format, status, book.format) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    if (book.format != "Audio" && book.readProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { book.readProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                BookActions(
                    book = book,
                    savingBookUri = savingBookUri,
                    onAction = onAction,
                )
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp)) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).padding(end = 4.dp, bottom = 3.dp)) {
                    Box(
                        modifier = Modifier.fillMaxSize().align(Alignment.Center).padding(start = 4.dp, top = 3.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 4.dp))
                            .background(Brush.horizontalGradient(listOf(colors.last(), colors.first()))),
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().shadow(7.dp, RoundedCornerShape(8.dp))
                            .graphicsLayer {
                                rotationY = -2.5f
                                cameraDistance = 18f * density
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(colors)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (book.format) {
                            "PDF" -> LocalPdfCover(
                                originalUri = book.uri,
                                offlineCopyUri = book.offlineCopyUri,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                            )
                            "EPUB" -> LocalEpubCover(
                                originalUri = book.uri,
                                offlineCopyUri = book.offlineCopyUri,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                            )
                            else -> Icon(
                                icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                        if (book.isFavorite) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = stringResource(R.string.accessibility_favorite_book),
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
                        Text(
                            book.title,
                            fontWeight = FontWeight.SemiBold,
                            color = cardInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        )
                        Text(book.format, color = mutedInk, fontSize = 10.sp, maxLines = 1)
                    }
                    BookActions(book = book, savingBookUri = savingBookUri, onAction = onAction)
                }
                if (book.format != "Audio" && book.readProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { book.readProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                val readingStatus = when {
                    book.format == "Audio" -> stringResource(R.string.book_status_audiobook)
                    book.readProgress >= 1f -> stringResource(R.string.book_status_finished)
                    book.readProgress > 0f -> stringResource(
                        R.string.book_progress_percent,
                        (book.readProgress.coerceIn(0f, 1f) * 100).toInt(),
                    )
                    else -> stringResource(R.string.book_status_not_started)
                }
                Text(
                    readingStatus,
                    color = mutedInk,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp, bottom = if (book.offlineCopyUri == null) 4.dp else 2.dp),
                )
                if (book.offlineCopyUri != null) {
                    Text(
                        stringResource(R.string.book_available_offline),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BookActions(
    book: LocalBook,
    savingBookUri: String?,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    onAction: (LibraryBookAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.accessibility_book_options, book.title),
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (book.isFavorite) R.string.book_remove_favorite else R.string.book_add_favorite))
                },
                onClick = {
                    expanded = false
                    onAction(LibraryBookAction.TOGGLE_FAVORITE)
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            when {
                                book.offlineCopyUri != null -> R.string.book_remove_offline_copy
                                savingBookUri == book.uri.toString() -> R.string.book_saving_copy
                                else -> R.string.book_save_offline
                            },
                        ),
                    )
                },
                enabled = savingBookUri != book.uri.toString(),
                onClick = {
                    expanded = false
                    onAction(
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.book_export_annotations)) },
                onClick = {
                    expanded = false
                    onAction(LibraryBookAction.EXPORT_ANNOTATIONS)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.book_organize_collection)) },
                onClick = {
                    expanded = false
                    onAction(LibraryBookAction.ORGANIZE)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_edit_title)) },
                onClick = {
                    expanded = false
                    onAction(LibraryBookAction.EDIT_TITLE)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_remove_title)) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAction(LibraryBookAction.REMOVE)
                },
            )
        }
    }
}
