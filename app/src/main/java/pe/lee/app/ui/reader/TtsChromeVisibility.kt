package pe.lee.app.ui.reader

/** Only the first user initiated playback may collapse the reading chrome. */
internal fun shouldHideReaderChromeAfterTtsStart(
    isFirstPlay: Boolean,
    touchExplorationEnabled: Boolean,
): Boolean = isFirstPlay && !touchExplorationEnabled
