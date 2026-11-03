package pe.lee.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import pe.lee.feature.library.R
import pe.lee.app.ui.theme.LeePeSpacing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A clean, functional library header, rather than a decorative bookshelf. */
@Composable
internal fun LibraryDashboardHeader(
    totalBooks: Int,
    busy: Boolean,
    onImport: () -> Unit,
    onScan: () -> Unit,
    onStats: () -> Unit,
    onBackup: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = LeePeSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LeePeSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .17f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.leepe_icon_foreground),
                    contentDescription = stringResource(R.string.accessibility_leepe_logo),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.library_offline_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onStats) {
                Icon(Icons.Default.BarChart, contentDescription = stringResource(R.string.accessibility_reading_statistics))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.accessibility_library_more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_backup_restore)) },
                        onClick = { menuExpanded = false; onBackup() }
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF163553), Color(0xFF263154), Color(0xFF322C5B))),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(LeePeSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(LeePeSpacing.md)
            ) {
                Text(
                    stringResource(if (totalBooks == 0) R.string.library_empty_welcome else R.string.library_welcome),
                    color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (totalBooks == 0) stringResource(R.string.library_empty_prompt)
                    else pluralStringResource(R.plurals.library_books_count, totalBooks, totalBooks),
                    color = Color(0xFFCEDBF5), style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE7E5FF),
                            contentColor = Color(0xFF24214B)
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(LeePeSpacing.xs + 2.dp))
                        Text(stringResource(R.string.action_import))
                    }
                    FilledTonalButton(
                        onClick = onScan,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF354D69),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(LeePeSpacing.xs + 2.dp))
                        Text(stringResource(if (busy) R.string.library_scanning else R.string.library_scan))
                    }
                }
            }
        }
    }
}

/** Switch between a compact list and the familiar cover grid. */
@Composable
internal fun LibraryDisplayToggle(
    listMode: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onModeChange(false) }, modifier = Modifier.semantics { selected = !listMode }) {
            Icon(
                Icons.Default.GridView,
                contentDescription = stringResource(R.string.accessibility_view_grid),
                tint = if (!listMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onModeChange(true) }, modifier = Modifier.semantics { selected = listMode }) {
            Icon(
                Icons.AutoMirrored.Filled.ViewList,
                contentDescription = stringResource(R.string.accessibility_view_list),
                tint = if (listMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
