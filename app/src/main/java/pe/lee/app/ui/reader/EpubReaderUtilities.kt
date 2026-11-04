@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package pe.lee.app.ui.reader

import android.content.Context
import android.net.Uri
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.shared.publication.Link
import pe.lee.app.R

internal const val NAVIGATOR_TAG = "lee_pe_epub_navigator"
internal const val MENU_HIGHLIGHT = 1
internal const val MENU_COPY_SELECTION = 2
internal const val MENU_SHARE_SELECTION = 3
internal const val ANNOTATION_DECORATION_GROUP = "lee_pe_annotations"
internal fun AndroidTtsEngine.Voice.isLocalSpanishVoice(): Boolean {
    if (requiresNetwork) return false
    val languageCode = language.toString()
        .substringBefore('-')
        .substringBefore('_')
    return languageCode.equals("es", ignoreCase = true)
}

internal fun spanishVoiceLabel(context: Context, voice: AndroidTtsEngine.Voice): String {
    val tag = voice.language.toString().uppercase()
    val label = when {
        tag.contains("MX") -> R.string.reader_voice_spanish_mexico
        tag.contains("ES") -> R.string.reader_voice_spanish_spain
        tag.contains("AR") -> R.string.reader_voice_spanish_argentina
        tag.contains("CO") -> R.string.reader_voice_spanish_colombia
        tag.contains("US") -> R.string.reader_voice_spanish_us
        else -> R.string.reader_language_spanish
    }
    return context.getString(label)
}

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
