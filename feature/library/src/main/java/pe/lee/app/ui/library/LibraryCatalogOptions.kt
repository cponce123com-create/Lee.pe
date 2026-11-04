package pe.lee.app.ui.library

import pe.lee.feature.library.R

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

/** The two visual arrangements shown in the bookshelf selector. */
internal enum class LibraryViewMode {
    COVERS,
    MIXED_BOOKSHELF
}

/** Optional ornaments drawn between books on the mixed bookshelf. */
internal enum class BookshelfAdornment(val labelRes: Int) {
    FLOWERS(R.string.library_adornment_flowers),
    PLANTS(R.string.library_adornment_plants),
    FIGURES(R.string.library_adornment_figures),
    NONE(R.string.library_adornment_none)
}
