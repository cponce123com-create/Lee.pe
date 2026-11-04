package pe.lee.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.lee.feature.library.R

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
    val galleryTypes = SHELF_DECORATION_GALLERY_TYPES
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
                            val isSelected = selectedDecoration == type
                            val decorationLabel = stringResource(type.labelRes)
                            Surface(
                                modifier = Modifier.width(132.dp).height(154.dp)
                                    .semantics {
                                        role = Role.RadioButton
                                        contentDescription = decorationLabel
                                        selected = isSelected
                                    }
                                    .clickable { selectedDecorationName = type.name },
                                shape = RoundedCornerShape(20.dp),
                                color = when {
                                    isSelected && darkTheme -> dialogColors.primaryContainer
                                    isSelected -> Color(0xFFE5F4EE)
                                    darkTheme -> dialogColors.surface
                                    else -> Color(0xFFFFFBF6)
                                },
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) dialogColors.primary else dialogColors.outlineVariant),
                                shadowElevation = if (isSelected) 5.dp else 2.dp
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    ShelfDecorationArt(type, Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp))
                                    Text(stringResource(type.labelRes), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = if (darkTheme && isSelected) dialogColors.onPrimaryContainer else dialogColors.onSurface)
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
internal fun ShelfDecorationArt(type: ShelfDecorationType, modifier: Modifier = Modifier) {
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
