package pe.lee.app.ui.library

import pe.lee.app.R

/** Stable filter values; labels are resolved from the current app locale. */
internal enum class LibraryFilter(val labelRes: Int) {
    ALL(R.string.library_filter_all),
    TO_READ(R.string.library_filter_to_read),
    IN_PROGRESS(R.string.library_filter_in_progress),
    COMPLETED(R.string.library_filter_completed),
    FAVORITES(R.string.library_filter_favorites),
    EPUB(R.string.library_filter_epub),
    PDF(R.string.library_filter_pdf),
    AUDIO(R.string.library_filter_audio),
    OFFLINE_COPY(R.string.library_filter_offline_copy)
}

/** Stable sort values; labels are resolved from the current app locale. */
internal enum class LibrarySortMode(val labelRes: Int) {
    TITLE_ASC(R.string.library_sort_title_ascending),
    TITLE_DESC(R.string.library_sort_title_descending),
    LAST_READ(R.string.library_sort_last_read),
    IMPORT_DATE(R.string.library_sort_import_date),
    PROGRESS(R.string.library_sort_progress)
}
