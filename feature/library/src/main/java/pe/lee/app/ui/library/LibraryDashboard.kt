package pe.lee.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.leepe_logo),
            contentDescription = stringResource(R.string.accessibility_leepe_logo),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF075B50),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onStats) {
            Icon(
                Icons.Default.BarChart,
                contentDescription = stringResource(R.string.accessibility_reading_statistics),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.accessibility_library_more_options),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (busy) R.string.library_scanning else R.string.library_scan))
                    },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
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
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_my_books),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFF075B50), RoundedCornerShape(12.dp))
                .clickable(onClickLabel = stringResource(R.string.accessibility_library_add_book), onClick = onImport)
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.accessibility_library_add_book),
                tint = Color.White,
            )
        }
    }
    if (totalBooks == 0) {
        Text(
            text = stringResource(R.string.library_empty_prompt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/** Two-state selector for the cover grid and mixed bookshelf. */
@Composable
internal fun LibraryViewModeSelector(viewMode: LibraryViewMode, onModeChange: (LibraryViewMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF3EADF),
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
                    color = if (selected) Color(0xFF075B50) else Color(0xFFF3EADF),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else Color(0xFF514A42),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
