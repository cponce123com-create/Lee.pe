@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package pe.lee.app.ui.reader

import android.content.Context
import android.net.Uri
import java.util.Locale
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.shared.publication.Link
import pe.lee.app.R

internal const val NAVIGATOR_TAG = "lee_pe_epub_navigator"
internal const val MENU_HIGHLIGHT = 1
internal const val MENU_COPY_SELECTION = 2
internal const val MENU_SHARE_SELECTION = 3
internal const val MENU_UNDERLINE_SELECTION = 4
internal const val MENU_SAVE_PHRASE = 5
internal const val MENU_DICTIONARY = 6
internal const val ANNOTATION_DECORATION_GROUP = "lee_pe_annotations"
internal fun AndroidTtsEngine.Voice.isLocalSpanishVoice(): Boolean {
    if (requiresNetwork) return false
    val languageCode = language.toString()
        .substringBefore('-')
        .substringBefore('_')
    return languageCode.equals("es", ignoreCase = true)
}

internal fun sortedLocalSpanishVoices(voices: Collection<AndroidTtsEngine.Voice>): List<AndroidTtsEngine.Voice> = voices
    .filter { it.isLocalSpanishVoice() }
    .sortedWith(compareBy({ it.language.toString() }, { it.id.value }))

/** Prefer the third installed es-US voice, falling back safely on this device's catalog. */
internal fun defaultSpanishUsVoice(voices: Collection<AndroidTtsEngine.Voice>): AndroidTtsEngine.Voice? {
    val localVoices = sortedLocalSpanishVoices(voices)
    val usVoices = localVoices.filter { spanishVoiceRegion(it.language.toString()) == "US" }
    return usVoices.getOrNull(2) ?: usVoices.firstOrNull() ?: localVoices.firstOrNull()
}

internal fun spanishVoiceLabel(context: Context, voice: AndroidTtsEngine.Voice): String {
    val region = spanishVoiceRegion(voice.language.toString())
    val label = when (region) {
        "MX" -> R.string.reader_voice_spanish_mexico
        "ES" -> R.string.reader_voice_spanish_spain
        "AR" -> R.string.reader_voice_spanish_argentina
        "CO" -> R.string.reader_voice_spanish_colombia
        "US" -> R.string.reader_voice_spanish_us
        "PE" -> R.string.reader_voice_spanish_peru
        "CL" -> R.string.reader_voice_spanish_chile
        else -> return if (region.isBlank()) {
            context.getString(R.string.reader_language_spanish)
        } else {
            context.getString(R.string.reader_voice_spanish_region, region)
        }
    }
    return context.getString(label)
}

internal fun spanishVoiceRegion(languageTag: String): String =
    Locale.forLanguageTag(languageTag.replace('_', '-')).country.uppercase(Locale.ROOT)

internal const val TTS_DECORATION_GROUP = "lee_pe_tts"
internal fun ttsSpeedPreferenceKey(uri: Uri): String = "tts-speed:$uri"
internal fun ttsVoicePreferenceKey(uri: Uri): String = "tts-voice:$uri"
internal const val DEFAULT_HIGHLIGHT_COLOR = 0xFFFFD54F.toInt()

internal data class TocEntry(val title: String, val link: Link, val depth: Int)

internal fun flattenContents(links: List<Link>, depth: Int = 0): List<TocEntry> = buildList {
    links.forEach { link ->
        add(TocEntry(link.title?.takeIf { it.isNotBlank() } ?: link.href.toString(), link, depth))
        addAll(flattenContents(link.children, depth + 1))
    }
}
