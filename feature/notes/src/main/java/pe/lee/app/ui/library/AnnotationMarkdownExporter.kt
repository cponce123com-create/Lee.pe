package pe.lee.app.ui.library

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import pe.lee.core.domain.ReaderAnnotation

/**
 * Creates a readable local Markdown export without including the book file or its private URI.
 */
object AnnotationMarkdownExporter {
    fun toMarkdown(
        bookTitle: String,
        format: String,
        annotations: List<ReaderAnnotation>,
        exportedAt: Long = System.currentTimeMillis(),
        pageLabel: (Int) -> String = { "Página $it" },
    ): String = buildString {
        appendLine("# Anotaciones: ${bookTitle.asSingleLine()}")
        appendLine()
        appendLine("- Formato: $format")
        appendLine("- Exportado: ${DateFormat.getDateTimeInstance().format(Date(exportedAt))}")
        appendLine("- Elementos: ${annotations.size}")
        appendLine()
        appendLine("---")
        appendLine()

        annotations.sortedBy { it.createdAt }.forEachIndexed { index, annotation ->
            appendLine("## ${index + 1}. ${annotation.kind.toReadableLabel()}")
            appendLine()
            annotation.locationDescription(pageLabel)?.let {
                appendLine("**Ubicación:** $it")
                appendLine()
            }
            annotation.selectedText?.takeIf(String::isNotBlank)?.let { selectedText ->
                appendLine("**Texto marcado:**")
                appendLine()
                selectedText.lines().forEach { appendLine("> ${it.ifBlank { " " }}") }
                appendLine()
            }
            annotation.noteText?.takeIf(String::isNotBlank)?.let { note ->
                appendLine("**Nota personal:**")
                appendLine()
                appendLine(note.trim())
                appendLine()
            }
            annotation.colorArgb?.let { color ->
                appendLine("**Color:** #${color.toUInt().toString(16).uppercase(Locale.ROOT).padStart(8, '0')}")
                appendLine()
            }
            appendLine("- Fecha: ${DateFormat.getDateTimeInstance().format(Date(annotation.createdAt))}")
            appendLine()
        }
    }

    private fun String.asSingleLine(): String = replace('\n', ' ').replace('\r', ' ').trim()

    private fun String.toReadableLabel(): String = when (uppercase(Locale.ROOT)) {
        "BOOKMARK" -> "Marcador"
        "HIGHLIGHT" -> "Subrayado o resaltado"
        "NOTE" -> "Nota"
        else -> replace('_', ' ').lowercase(Locale.ROOT)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun ReaderAnnotation.locationDescription(pageLabel: (Int) -> String): String? = runCatching {
        val locator = JSONObject(locatorJson)
        val pageIndex = locator.optInt("pageIndex", -1)
        if (pageIndex >= 0) {
            pageLabel(pageIndex + 1)
        } else {
            val locations = locator.optJSONObject("locations")
            val href = locator.optString("href").takeIf { it.isNotBlank() }
                ?: locator.optString("resource").takeIf { it.isNotBlank() }
            val progression = locations?.optDouble("totalProgression", Double.NaN)
                ?.takeIf { !it.isNaN() }
                ?.let { "${(it.coerceIn(0.0, 1.0) * 100).toInt()}%" }
            listOfNotNull(href, progression).joinToString(" · ").takeIf { it.isNotBlank() }
        }
    }.getOrNull()
}

fun annotationExportFileName(title: String, fallbackBookLabel: String = "libro"): String {
    val safeTitle = title.trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.', '-')
        .take(80)
        .ifBlank { fallbackBookLabel }
    return "${safeTitle}_anotaciones.md"
}
