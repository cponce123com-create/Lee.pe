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
import androidx.compose.material.icons.filled.ViewList
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
import pe.lee.app.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    contentDescription = "Logotipo de Lee.pe",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Lee.pe", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Lector personal · Sin conexión", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onStats) {
                Icon(Icons.Default.BarChart, contentDescription = "Estadísticas de lectura")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones de biblioteca")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Respaldo y restauración") },
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
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (totalBooks == 0) "Tu próxima historia comienza aquí" else "Tu biblioteca, a tu manera",
                    color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (totalBooks == 0) "Añade libros o escanea una carpeta de tu teléfono."
                    else "$totalBooks archivos en tu biblioteca. Todo se queda en tu dispositivo.",
                    color = Color(0xFFCEDBF5), fontSize = 13.sp
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
                        Spacer(Modifier.size(6.dp))
                        Text("Importar")
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
                        Spacer(Modifier.size(6.dp))
                        Text(if (busy) "Escaneando" else "Escanear")
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
        IconButton(onClick = { onModeChange(false) }) {
            Icon(
                Icons.Default.GridView,
                contentDescription = "Ver portadas en cuadrícula",
                tint = if (!listMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onModeChange(true) }) {
            Icon(
                Icons.Default.ViewList,
                contentDescription = "Ver libros en lista",
                tint = if (listMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
