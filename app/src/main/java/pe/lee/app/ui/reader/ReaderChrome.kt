package pe.lee.app.ui.reader

import android.view.Menu
import android.view.MenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.app.R
import pe.lee.core.domain.ReaderAnnotation
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
internal fun ReaderChrome(
    title: String,
    ttsIsActive: Boolean,
    ttsIsPlaying: Boolean,
    onToggleTts: () -> Unit,
    progress: Float,
    annotations: List<ReaderAnnotation>,
    onAddBookmark: () -> Unit,
    onAddNote: (String) -> Unit,
    onUpdateAnnotation: (ReaderAnnotation, String) -> Unit,
    onChangeHighlightColor: (ReaderAnnotation, Int) -> Unit,
    onSelectAnnotation: (ReaderAnnotation) -> Unit,
    onDeleteAnnotation: (ReaderAnnotation) -> Unit,
    preferences: EpubPreferences,
    brightness: Float,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit,
    onToggleChrome: () -> Unit,
    contents: List<TocEntry>,
    searchPublication: Publication?,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectContent: (Link) -> Unit,
    onSelectSearchResult: (Locator) -> Unit,
    onSeek: (Float) -> Unit,
    onProgressChange: (Float) -> Unit
) {
    var showContents by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    val showAnnotationsState = remember { mutableStateOf(false) }
    var showAnnotations by showAnnotationsState
    val showNoteEditorState = remember { mutableStateOf(false) }
    var showNoteEditor by showNoteEditorState
    val editingAnnotationState = remember { mutableStateOf<ReaderAnnotation?>(null) }
    var editingAnnotation by editingAnnotationState
    val noteDraftState = remember { mutableStateOf("") }
    var noteDraft by noteDraftState
    var showAppearance by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val searchQueryState = remember { mutableStateOf("") }
    val searchResultsState = remember { mutableStateOf<List<Locator>>(emptyList()) }
    val hasSearchedState = remember { mutableStateOf(false) }
    val searchingState = remember { mutableStateOf(false) }
    val searchErrorState = remember { mutableStateOf<String?>(null) }
    val searchLimitReachedState = remember { mutableStateOf(false) }
    var targetPercent by remember { mutableStateOf("") }
    val targetProgress = targetPercent.replace(',', '.').toFloatOrNull()
        ?.takeIf { it in 0f..100f }

    if (showSearch) EpubSearchSheet(
        searchPublication = searchPublication,
        searchQuery = searchQueryState,
        searchResults = searchResultsState,
        hasSearched = hasSearchedState,
        searching = searchingState,
        searchError = searchErrorState,
        searchLimitReached = searchLimitReachedState,
        onDismiss = { showSearch = false },
        onSelectResult = onSelectSearchResult
    )

    if (showAnnotations || showNoteEditor) EpubAnnotationSheets(
        annotations = annotations,
        showAnnotations = showAnnotationsState,
        showNoteEditor = showNoteEditorState,
        editingAnnotation = editingAnnotationState,
        noteDraft = noteDraftState,
        onAddBookmark = onAddBookmark,
        onAddNote = onAddNote,
        onUpdateAnnotation = onUpdateAnnotation,
        onChangeHighlightColor = onChangeHighlightColor,
        onSelectAnnotation = onSelectAnnotation,
        onDeleteAnnotation = onDeleteAnnotation
    )

    if (showAppearance) {
        ModalBottomSheet(onDismissRequest = { showAppearance = false }) {
            EpubAppearanceSheet(
                preferences = preferences,
                brightness = brightness,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onBrightnessChange = onBrightnessChange,
                onPreferencesChange = onPreferencesChange
            )
        }
    }

    if (showGoTo) {
        AlertDialog(
            onDismissRequest = { showGoTo = false },
            title = { Text(stringResource(R.string.reader_go_to_position)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.reader_go_to_description)
                    )
                    OutlinedTextField(
                        value = targetPercent,
                        onValueChange = { input ->
                            targetPercent = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text(stringResource(R.string.reader_progress_label)) },
                        suffix = { Text("%") },
                        singleLine = true,
                        isError = targetProgress == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = targetProgress != null,
                    onClick = {
                        targetProgress?.let { value ->
                            val normalized = value / 100f
                            onProgressChange(normalized)
                            onSeek(normalized)
                            showGoTo = false
                        }
                    }
                ) { Text(stringResource(R.string.reader_go)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoTo = false }) { Text(stringResource(R.string.reader_cancel)) }
            }
        )
    }

    if (showContents) {
        ModalBottomSheet(onDismissRequest = { showContents = false }) {
            Text(
                stringResource(R.string.reader_contents_title),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (contents.isEmpty()) {
                Text(
                    stringResource(R.string.reader_contents_empty),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(contents) { entry ->
                        Text(
                            text = entry.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectContent(entry.link)
                                    showContents = false
                                }
                                .padding(
                                    start = (22 + entry.depth * 16).dp,
                                    end = 20.dp,
                                    top = 13.dp,
                                    bottom = 13.dp
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Compact toolbar: the original seven actions could not fit on narrow phones.
        // Keep frequent actions visible and put everything else in an accessible menu.
        Surface(
            color = Color(0xD90A1423),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onToggleTts) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = when {
                            ttsIsActive && ttsIsPlaying -> stringResource(R.string.reader_tts_pause)
                            ttsIsActive -> stringResource(R.string.reader_tts_resume)
                            else -> stringResource(R.string.reader_tts_start)
                        },
                        tint = if (ttsIsActive) Color(0xFF56D7FF) else Color.White
                    )
                }
                Box {
                    IconButton(onClick = { showActionsMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.reader_more_options),
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_search_book)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = { showActionsMenu = false; showSearch = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_bookmarks_notes)) },
                            leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                            onClick = { showActionsMenu = false; showAnnotations = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_appearance_title)) },
                            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                            onClick = { showActionsMenu = false; showAppearance = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_book_index)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null) },
                            onClick = { showActionsMenu = false; showContents = true }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_hide_controls)) },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                            onClick = { showActionsMenu = false; onToggleChrome() }
                        )
                    }
                }
            }
        }

        Surface(
            color = Color(0xE60A1423),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                val progressLabel = stringResource(R.string.reader_progress_label)
                Slider(
                    value = progress,
                    onValueChange = onProgressChange,
                    onValueChangeFinished = { onSeek(progress.coerceIn(0f, 1f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        // TalkBack announced only the percentage, without saying
                        // what the slider controls (A-19).
                        .semantics {
                            contentDescription = progressLabel
                        }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.reader_previous_page),
                            tint = Color.White
                        )
                    }
                    TextButton(
                        onClick = {
                            targetPercent = (progress * 100).toInt().toString()
                            showGoTo = true
                        }
                    ) {
                        Text(
                            stringResource(R.string.reader_go_to_percent, (progress * 100).toInt()),
                            color = Color(0xFFD3DCEC),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.reader_next_page),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
