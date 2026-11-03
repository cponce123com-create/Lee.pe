package pe.lee.app.ui.reader

import android.net.Uri
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.r2.shared.publication.Link

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

internal fun spanishVoiceLabel(voice: AndroidTtsEngine.Voice): String {
    val tag = voice.language.toString().uppercase()
    return when {
        tag.contains("MX") -> "Español (México)"
        tag.contains("ES") -> "Español (España)"
        tag.contains("AR") -> "Español (Argentina)"
        tag.contains("CO") -> "Español (Colombia)"
        tag.contains("US") -> "Español (Estados Unidos)"
        else -> "Español"
    }
}

internal const val TTS_DECORATION_GROUP = "lee_pe_tts"
internal fun ttsSpeedPreferenceKey(uri: Uri): String = "tts-speed:$uri"
internal fun ttsVoicePreferenceKey(uri: Uri): String = "tts-voice:$uri"
internal const val DEFAULT_HIGHLIGHT_COLOR = 0xFFFFD54F.toInt()

internal data class TocEntry(
    val title: String,
    val link: Link,
    val depth: Int
)

internal fun flattenContents(links: List<Link>, depth: Int = 0): List<TocEntry> =
    buildList {
        links.forEach { link ->
            add(TocEntry(link.title?.takeIf { it.isNotBlank() } ?: link.href.toString(), link, depth))
            addAll(flattenContents(link.children, depth + 1))
        }
    }
