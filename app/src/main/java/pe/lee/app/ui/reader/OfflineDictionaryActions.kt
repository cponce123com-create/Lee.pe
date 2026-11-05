package pe.lee.app.ui.reader

import android.app.AlertDialog
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.lee.app.R

/** Shows the first selected word, since dictionary lookup is a headword exact match. */
internal fun showOfflineDictionary(context: Context, scope: CoroutineScope, selection: String) {
    val term = WORD_PATTERN.find(selection)?.value.orEmpty()
    if (term.isBlank()) return

    val dialog = AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.dictionary_title))
        .setMessage(context.getString(R.string.dictionary_loading))
        .setPositiveButton(R.string.dictionary_close, null)
        .create()
    dialog.show()

    scope.launch {
        val result = runCatching { OfflineDictionaryRepository(context).lookup(term) }.getOrNull()
        val message = result?.let { entry ->
            buildString {
                append(entry.headword)
                append("\n\n")
                append(entry.definitions.joinToString("\n\n") { "• $it" }.take(MAX_DISPLAY_CHARS))
            }
        } ?: context.getString(R.string.dictionary_not_found, term)
        withContext(Dispatchers.Main) {
            if (dialog.isShowing) dialog.setMessage(message)
        }
    }
}

private val WORD_PATTERN = Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)?")
private const val MAX_DISPLAY_CHARS = 8_000
