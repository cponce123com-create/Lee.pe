package pe.lee.app.ui.reader

import androidx.annotation.StringRes
import pe.lee.app.R

internal enum class PageTurnStyle(@StringRes val label: Int) {
    CURL(R.string.reader_turn_curl),
    SLIDE(R.string.reader_turn_slide),
    NONE(R.string.reader_turn_none),
}

internal const val PAGE_TURN_STYLE_KEY = "epubPageTurnStyle"
