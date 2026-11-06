package pe.lee.app.ui.reader

internal class EpubPageTurnController {
    var onStart: ((Boolean, () -> Boolean) -> Unit)? = null
    var onBeginDrag: ((Boolean) -> Boolean)? = null
    var onUpdateDrag: ((Float) -> Unit)? = null
    var onEndDrag: ((Boolean, ((Boolean) -> Boolean)?) -> Unit)? = null
    var onCancel: (() -> Unit)? = null
    var isBusy: (() -> Boolean)? = null

    fun turn(isForward: Boolean, navigate: () -> Boolean) {
        val start = onStart
        if (start == null) navigate() else start(isForward, navigate)
    }

    fun beginDrag(forward: Boolean): Boolean = onBeginDrag?.invoke(forward) == true

    fun updateDrag(progress: Float) {
        onUpdateDrag?.invoke(progress)
    }

    fun endDrag(commit: Boolean, navigate: ((Boolean) -> Boolean)?) {
        onEndDrag?.invoke(commit, navigate)
    }

    fun cancel() {
        onCancel?.invoke()
    }
}
