package pe.lee.app.ui.reader

/**
 * Follows engine word locations when available, falling back to the utterance.
 * Pausing releases the last position so resuming can refocus the same word.
 * Rejected navigation is retried rather than silently losing synchronization.
 */
internal class NarrationFollower<T>(private val identity: (T) -> String, private val navigate: (T) -> Boolean) {
    private var lastAccepted: String? = null

    fun update(isPlaying: Boolean, utterance: T, token: T?): Boolean {
        if (!isPlaying) {
            lastAccepted = null
            return false
        }
        val target = token ?: utterance
        val key = identity(target)
        if (key == lastAccepted) return false
        val accepted = navigate(target)
        if (accepted) lastAccepted = key
        return accepted
    }
}
