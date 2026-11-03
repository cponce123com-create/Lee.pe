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
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    .background(Color(0xFF211744), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = Color(0xFFC5A9FF),
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                if (hasBooks) "No encontramos coincidencias" else "Tu próxima historia empieza aquí",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (hasBooks) "Prueba otro título o cambia el filtro."
                else "Importa un EPUB, PDF o audiolibro desde tu dispositivo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasBooks) {
                Button(onClick = onImport, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Elegir archivos")
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
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onSaveOfflineCopy: () -> Unit,
    onRemoveOfflineCopy: () -> Unit,
    onExportAnnotations: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTitle: () -> Unit,
    onOrganize: () -> Unit
) {
    val (icon, colors) = when (book.format) {
        "PDF" -> Icons.Default.PictureAsPdf to listOf(Color(0xFF5C2A70), Color(0xFF1F4C70))
        "Audio" -> Icons.Default.Headphones to listOf(Color(0xFF145D67), Color(0xFF26396F))
        else -> Icons.Default.MenuBook to listOf(Color(0xFF603680), Color(0xFF204D74))
    }

    Card(
        modifier = modifier.clickable(onClickLabel = "Abrir ${book.title}", onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            book.format == "Audio" -> "Audiolibro"
                            book.readProgress >= 1f -> "Terminado"
                            book.readProgress > 0f -> "${(book.readProgress * 100).toInt()}% leído"
                            else -> "Por leer"
                        } + " · ${book.format}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (book.format != "Audio" && book.readProgress > 0f) {
                        LinearProgressIndicator(
                            progress = book.readProgress.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                BookActions(
                    book = book,
                    savingBookUri = savingBookUri,
                    onRemove = onRemove,
                    onSaveOfflineCopy = onSaveOfflineCopy,
                    onRemoveOfflineCopy = onRemoveOfflineCopy,
                    onExportAnnotations = onExportAnnotations,
                    onToggleFavorite = onToggleFavorite,
                    onEditTitle = onEditTitle,
                    onOrganize = onOrganize
                )
            }
        } else {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
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
                        contentDescription = "Favorito",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    )
                }
                BookActions(
                    book = book,
                    savingBookUri = savingBookUri,
                    modifier = Modifier.align(Alignment.TopEnd),
                    iconTint = Color.White,
                    onRemove = onRemove,
                    onSaveOfflineCopy = onSaveOfflineCopy,
                    onRemoveOfflineCopy = onRemoveOfflineCopy,
                    onExportAnnotations = onExportAnnotations,
                    onToggleFavorite = onToggleFavorite,
                    onEditTitle = onEditTitle,
                    onOrganize = onOrganize
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.format != "Audio" && book.readProgress > 0f) {
                LinearProgressIndicator(
                    progress = book.readProgress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            val readingStatus = when {
                book.format == "Audio" -> "Audiolibro"
                book.readProgress >= 1f -> "Terminado"
                book.readProgress > 0f -> "${(book.readProgress.coerceIn(0f, 1f) * 100).toInt()}% leído"
                else -> "Sin empezar"
            }
            Text(
                readingStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp, bottom = if (book.offlineCopyUri == null) 4.dp else 2.dp)
            )
            if (book.offlineCopyUri != null) {
                Text(
                    "Disponible sin conexión",
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
    onRemove: () -> Unit,
    onSaveOfflineCopy: () -> Unit,
    onRemoveOfflineCopy: () -> Unit,
    onExportAnnotations: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTitle: () -> Unit,
    onOrganize: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${book.title}", tint = iconTint)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (book.isFavorite) "Quitar de favoritos" else "Agregar a favoritos") },
                            onClick = {
                                expanded = false
                                onToggleFavorite()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (book.offlineCopyUri != null) "Quitar copia sin conexión" else if (savingBookUri == book.uri.toString()) "Guardando copia…" else "Guardar sin conexión")
                            },
                            enabled = savingBookUri != book.uri.toString(),
                            onClick = {
                                expanded = false
                                if (book.offlineCopyUri != null) onRemoveOfflineCopy() else onSaveOfflineCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Exportar anotaciones a Markdown") },
                            onClick = {
                                expanded = false
                                onExportAnnotations()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Organizar en colección") },
                            onClick = {
                                expanded = false
                                onOrganize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar título") },
                            onClick = {
                                expanded = false
                                onEditTitle()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Quitar de la biblioteca") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onRemove()
                            }
                        )
                    }
                }

}
