package pe.lee.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.feature.library.R

/** Compact library header styled after the Lee.pe bookshelf reference. */
@Composable
internal fun LibraryDashboardHeader(
    totalBooks: Int,
    busy: Boolean,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onStats: () -> Unit,
    onBackup: () -> Unit,
    onSearch: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.leepe_logo),
            contentDescription = stringResource(R.string.accessibility_leepe_logo),
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Lee.pe",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.shelf_book_count, totalBooks),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Search, stringResource(R.string.accessibility_search), tint = MaterialTheme.colorScheme.primary)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    stringResource(R.string.accessibility_library_more_options),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.accessibility_reading_statistics)) },
                    onClick = {
                        menuExpanded = false
                        onStats()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_version, appVersion)) },
                    enabled = false,
                    onClick = {},
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (busy) R.string.library_scanning else R.string.library_scan)) },
                    enabled = !busy,
                    onClick = {
                        menuExpanded = false
                        onScan()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_backup_restore)) },
                    onClick = {
                        menuExpanded = false
                        onBackup()
                    },
                )
            }
        }
        Box(
            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp))
                .clickable(onClickLabel = stringResource(R.string.accessibility_library_add_book), onClick = onImport)
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.accessibility_library_add_book), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Two-state selector for the cover grid and mixed bookshelf. */
@Composable
internal fun LibraryViewModeSelector(viewMode: LibraryViewMode, onModeChange: (LibraryViewMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0x77342217),
    ) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            LibraryViewMode.entries.forEach { mode ->
                val selected = viewMode == mode
                val label = stringResource(
                    if (mode == LibraryViewMode.COVERS) {
                        R.string.library_view_covers
                    } else {
                        R.string.library_view_mixed
                    },
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            this.selected = selected
                            role = Role.RadioButton
                        }
                        .clickable(onClickLabel = label, onClick = { onModeChange(mode) }),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) Color(0xFF007C69) else Color(0x77342217),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else Color(0xFFFFE7B3),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
