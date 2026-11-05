@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package pe.lee.app.ui.reader

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.shared.ExperimentalReadiumApi
import pe.lee.app.R

@OptIn(ExperimentalReadiumApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    val selectedVoice = voices.firstOrNull { it.id.value == selectedVoiceId }
    val narrationFallback = stringResource(R.string.reader_tts_book_narration)
    val voiceInstallHint = stringResource(R.string.reader_voice_install_hint)
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        color = Color(0xF20A1423),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                sentence.ifBlank { narrationFallback },
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
            )
            error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp) }
            // Five controls in a plain Row overflowed on 360 dp screens with large
            // fonts; FlowRow wraps them into a second line instead (A-19).
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) {
                            stringResource(R.string.reader_tts_pause_voice)
                        } else {
                            stringResource(R.string.reader_tts_continue_voice)
                        },
                        tint = Color.White,
                    )
                }
                TextButton(onClick = {
                    val index = speeds.indexOf(speed).coerceAtLeast(0)
                    onSpeedChange(speeds[(index + 1) % speeds.size])
                }) { Text("${speed}x") }
                Box {
                    TextButton(onClick = { voiceMenuExpanded = true }) {
                        Text(
                            selectedVoice?.let { spanishVoiceLabel(context, it) }
                                ?: stringResource(R.string.reader_language_spanish),
                        )
                    }
                    DropdownMenu(
                        expanded = voiceMenuExpanded,
                        onDismissRequest = { voiceMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_voice_automatic)) },
                            onClick = {
                                onVoiceChange(null)
                                voiceMenuExpanded = false
                            },
                        )
                        if (voices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reader_voice_none_local)) },
                                enabled = false,
                                onClick = {},
                            )
                        } else {
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text("${spanishVoiceLabel(context, voice)} · ${voice.id.value}") },
                                    onClick = {
                                        onVoiceChange(voice)
                                        voiceMenuExpanded = false
                                    },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_voice_manage_device)) },
                            onClick = {
                                voiceMenuExpanded = false
                                try {
                                    context.startActivity(
                                        Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        voiceInstallHint,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                        )
                    }
                }
                TextButton(onClick = onTimerChange) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Text(
                        if (timerMinutes == 0) {
                            stringResource(R.string.reader_timer)
                        } else {
                            stringResource(R.string.reader_timer_minutes, timerMinutes)
                        },
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = stringResource(R.string.reader_tts_stop),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}


@Composable
internal fun TtsMiniControls(
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = stringResource(
        if (isPlaying) R.string.reader_tts_playing_status else R.string.reader_tts_paused_status,
    )
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        color = Color(0xF20A1423),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(status, modifier = Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onTogglePlayback, modifier = Modifier.semantics { role = Role.Button }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.reader_tts_pause_voice else R.string.reader_tts_continue_voice,
                    ),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onStop, modifier = Modifier.semantics { role = Role.Button }) {
                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.reader_tts_stop), tint = Color.White)
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
    onPreferencesChange: (EpubPreferences) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            stringResource(R.string.reader_appearance_title),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reader_theme_title), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val selectedTheme = selectedReaderTheme(preferences)
                listOf(
                    R.string.reader_theme_light to ReaderThemeChoice.LIGHT,
                    R.string.reader_theme_sepia to ReaderThemeChoice.SEPIA,
                    R.string.reader_theme_dark to ReaderThemeChoice.DARK,
                    R.string.reader_theme_oled to ReaderThemeChoice.OLED,
                ).forEach { (labelRes, choice) ->
                    FilterChip(
                        selected = selectedTheme == choice,
                        onClick = {
                            val updated = when (choice) {
                                ReaderThemeChoice.SEPIA -> preferences.copy(
                                    theme = ReadiumTheme.SEPIA,
                                    backgroundColor = null,
                                    textColor = null,
                                )
                                ReaderThemeChoice.DARK -> preferences.copy(
                                    theme = ReadiumTheme.DARK,
                                    backgroundColor = null,
                                    textColor = null,
                                )
                                ReaderThemeChoice.OLED -> preferences.copy(
                                    theme = ReadiumTheme.DARK,
                                    backgroundColor = ReadiumColor(0xFF000000.toInt()),
                                    textColor = ReadiumColor(0xFFFFFFFF.toInt()),
                                )
                                ReaderThemeChoice.LIGHT -> preferences.copy(
                                    theme = ReadiumTheme.LIGHT,
                                    backgroundColor = null,
                                    textColor = null,
                                )
                            }
                            onPreferencesChange(updated)
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.reader_font_size, (fontSize * 100).toInt()),
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(fontSize = fontSize.toDouble()))
                },
                valueRange = 0.75f..1.75f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reader_text_style_title), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferences.publisherStyles != false,
                    onClick = { onPreferencesChange(preferences.copy(publisherStyles = true)) },
                    label = { Text(stringResource(R.string.reader_text_style_publisher)) },
                )
                FilterChip(
                    selected = preferences.publisherStyles == false,
                    onClick = { onPreferencesChange(preferences.copy(publisherStyles = false)) },
                    label = { Text(stringResource(R.string.reader_text_style_app)) },
                )
            }
            Text(
                stringResource(R.string.reader_text_style_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.reader_line_height, (lineHeight * 100).toInt()),
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = lineHeight,
                onValueChange = { lineHeight = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(lineHeight = lineHeight.toDouble(), publisherStyles = false))
                },
                valueRange = 1f..2f,
                steps = 9,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.reader_margins, (pageMargins * 100).toInt()),
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = pageMargins,
                onValueChange = { pageMargins = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(pageMargins = pageMargins.toDouble()))
                },
                valueRange = 0.5f..2f,
                steps = 14,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reader_alignment_title), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    R.string.reader_align_start to ReadiumTextAlign.START,
                    R.string.reader_align_justify to ReadiumTextAlign.JUSTIFY,
                    R.string.reader_align_center to ReadiumTextAlign.CENTER,
                ).forEach { (labelRes, alignment) ->
                    FilterChip(
                        selected = (preferences.textAlign ?: ReadiumTextAlign.START) == alignment,
                        onClick = {
                            onPreferencesChange(preferences.copy(textAlign = alignment, publisherStyles = false))
                        },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reader_navigation_title), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferences.scroll != true,
                    onClick = { onPreferencesChange(preferences.copy(scroll = false)) },
                    label = { Text(stringResource(R.string.reader_navigation_paginated)) },
                )
                FilterChip(
                    selected = preferences.scroll == true,
                    onClick = { onPreferencesChange(preferences.copy(scroll = true)) },
                    label = { Text(stringResource(R.string.reader_navigation_scroll)) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reader_while_reading_title), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_keep_screen_on))
                    Text(
                        stringResource(R.string.reader_keep_screen_on_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.reader_brightness_title), fontWeight = FontWeight.SemiBold)
            FilterChip(
                selected = brightness < 0f,
                onClick = { onBrightnessChange(-1f) },
                label = { Text(stringResource(R.string.reader_brightness_system)) },
            )
            Slider(
                value = if (brightness < 0f) 0.7f else brightness.coerceIn(0.15f, 1f),
                onValueChange = onBrightnessChange,
                valueRange = 0.15f..1f,
                enabled = brightness >= 0f,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reader_typography_title), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf<Pair<Int, ReadiumFontFamily?>>(
                    R.string.reader_font_original to null,
                    R.string.reader_font_serif to ReadiumFontFamily.SERIF,
                    R.string.reader_font_sans to ReadiumFontFamily.SANS_SERIF,
                    R.string.reader_font_dyslexic to ReadiumFontFamily.OPEN_DYSLEXIC,
                ).forEach { (labelRes, family) ->
                    FilterChip(
                        selected = preferences.fontFamily?.name == family?.name,
                        onClick = { onPreferencesChange(preferences.copy(fontFamily = family)) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private enum class ReaderThemeChoice { LIGHT, SEPIA, DARK, OLED }

private fun selectedReaderTheme(preferences: EpubPreferences): ReaderThemeChoice = when {
    preferences.theme == ReadiumTheme.DARK &&
        preferences.backgroundColor?.int == 0xFF000000.toInt() -> ReaderThemeChoice.OLED
    preferences.theme == ReadiumTheme.SEPIA -> ReaderThemeChoice.SEPIA
    preferences.theme == ReadiumTheme.DARK -> ReaderThemeChoice.DARK
    else -> ReaderThemeChoice.LIGHT
}
