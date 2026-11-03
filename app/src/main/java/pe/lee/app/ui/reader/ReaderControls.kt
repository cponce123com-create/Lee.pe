@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package pe.lee.app.ui.reader

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun TtsControls(
    sentence: String,
    isPlaying: Boolean,
    speed: Float,
    voices: List<AndroidTtsEngine.Voice>,
    selectedVoiceId: String?,
    timerMinutes: Int,
    error: String?,
    onTogglePlayback: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVoiceChange: (AndroidTtsEngine.Voice?) -> Unit,
    onTimerChange: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    val selectedVoice = voices.firstOrNull { it.id.value == selectedVoiceId }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        color = Color(0xF20A1423),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                sentence.ifBlank { "Narración del libro" },
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
            error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar voz" else "Continuar voz",
                        tint = Color.White
                    )
                }
                TextButton(onClick = {
                    val index = speeds.indexOf(speed).coerceAtLeast(0)
                    onSpeedChange(speeds[(index + 1) % speeds.size])
                }) { Text("${speed}x") }
                Box {
                    TextButton(onClick = { voiceMenuExpanded = true }) {
                        Text(selectedVoice?.let(::spanishVoiceLabel) ?: "Español")
                    }
                    DropdownMenu(
                        expanded = voiceMenuExpanded,
                        onDismissRequest = { voiceMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Automática (español)") },
                            onClick = {
                                onVoiceChange(null)
                                voiceMenuExpanded = false
                            }
                        )
                        if (voices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No hay voces locales en español") },
                                enabled = false,
                                onClick = {}
                            )
                        } else {
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text("${spanishVoiceLabel(voice)} · ${voice.id.value}") },
                                    onClick = {
                                        onVoiceChange(voice)
                                        voiceMenuExpanded = false
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Gestionar voces del dispositivo") },
                            onClick = {
                                voiceMenuExpanded = false
                                try {
                                    context.startActivity(
                                        Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Busca Español (México) en Ajustes > Accesibilidad > Salida de texto a voz.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }
                TextButton(onClick = onTimerChange) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Text(if (timerMinutes == 0) "Temporizador" else "${timerMinutes} min")
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Detener narración", tint = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun EpubAppearanceSheet(
    preferences: EpubPreferences,
    brightness: Float,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit
) {
    var fontSize by remember(preferences.fontSize) {
        mutableFloatStateOf((preferences.fontSize ?: 1.0).toFloat().coerceIn(0.75f, 1.75f))
    }
    var lineHeight by remember(preferences.lineHeight) {
        mutableFloatStateOf((preferences.lineHeight ?: 1.2).toFloat().coerceIn(1f, 2f))
    }
    var pageMargins by remember(preferences.pageMargins) {
        mutableFloatStateOf((preferences.pageMargins ?: 1.0).toFloat().coerceIn(0.5f, 2f))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Apariencia de lectura", fontSize = 21.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tema", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Claro", "Sepia", "Oscuro", "OLED").forEach { label ->
                    FilterChip(
                        selected = selectedReaderTheme(preferences) == label,
                        onClick = {
                            val updated = when (label) {
                                "Sepia" -> preferences.copy(theme = ReadiumTheme.SEPIA, backgroundColor = null, textColor = null)
                                "Oscuro" -> preferences.copy(theme = ReadiumTheme.DARK, backgroundColor = null, textColor = null)
                                "OLED" -> preferences.copy(
                                    theme = ReadiumTheme.DARK,
                                    backgroundColor = ReadiumColor(0xFF000000.toInt()),
                                    textColor = ReadiumColor(0xFFFFFFFF.toInt())
                                )
                                else -> preferences.copy(theme = ReadiumTheme.LIGHT, backgroundColor = null, textColor = null)
                            }
                            onPreferencesChange(updated)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tamaño de letra · ${(fontSize * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(fontSize = fontSize.toDouble()))
                },
                valueRange = 0.75f..1.75f
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estilo del texto", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferences.publisherStyles != false,
                    onClick = { onPreferencesChange(preferences.copy(publisherStyles = true)) },
                    label = { Text("Estilo del libro") }
                )
                FilterChip(
                    selected = preferences.publisherStyles == false,
                    onClick = { onPreferencesChange(preferences.copy(publisherStyles = false)) },
                    label = { Text("Ajustes Lee.pe") }
                )
            }
            Text(
                "Interlineado y alineación necesitan los ajustes Lee.pe para reemplazar el formato definido por el EPUB.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Interlineado · ${(lineHeight * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = lineHeight,
                onValueChange = { lineHeight = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(lineHeight = lineHeight.toDouble(), publisherStyles = false))
                },
                valueRange = 1f..2f,
                steps = 9
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Márgenes laterales · ${(pageMargins * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = pageMargins,
                onValueChange = { pageMargins = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(pageMargins = pageMargins.toDouble()))
                },
                valueRange = 0.5f..2f,
                steps = 14
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alineación", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Inicio" to ReadiumTextAlign.START,
                    "Justificada" to ReadiumTextAlign.JUSTIFY,
                    "Centro" to ReadiumTextAlign.CENTER
                ).forEach { (label, alignment) ->
                    FilterChip(
                        selected = (preferences.textAlign ?: ReadiumTextAlign.START) == alignment,
                        onClick = {
                            onPreferencesChange(preferences.copy(textAlign = alignment, publisherStyles = false))
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Navegación", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferences.scroll != true,
                    onClick = { onPreferencesChange(preferences.copy(scroll = false)) },
                    label = { Text("Por páginas") }
                )
                FilterChip(
                    selected = preferences.scroll == true,
                    onClick = { onPreferencesChange(preferences.copy(scroll = true)) },
                    label = { Text("Desplazamiento") }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Durante la lectura", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mantener pantalla encendida")
                    Text("Evita que se apague mientras lees.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Brillo de pantalla", fontWeight = FontWeight.SemiBold)
            FilterChip(
                selected = brightness < 0f,
                onClick = { onBrightnessChange(-1f) },
                label = { Text("Usar brillo del sistema") }
            )
            Slider(
                value = if (brightness < 0f) 0.7f else brightness.coerceIn(0.15f, 1f),
                onValueChange = onBrightnessChange,
                valueRange = 0.15f..1f,
                enabled = brightness >= 0f
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tipografía", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Original" to null,
                    "Serif" to ReadiumFontFamily.SERIF,
                    "Sans" to ReadiumFontFamily.SANS_SERIF,
                    "Dislexia" to ReadiumFontFamily.OPEN_DYSLEXIC
                ).forEach { (label, family) ->
                    FilterChip(
                        selected = preferences.fontFamily?.name == family?.name,
                        onClick = { onPreferencesChange(preferences.copy(fontFamily = family)) },
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun selectedReaderTheme(preferences: EpubPreferences): String = when {
    preferences.theme == ReadiumTheme.DARK &&
        preferences.backgroundColor?.int == 0xFF000000.toInt() -> "OLED"
    preferences.theme == ReadiumTheme.SEPIA -> "Sepia"
    preferences.theme == ReadiumTheme.DARK -> "Oscuro"
    else -> "Claro"
}
