package pe.lee.feature.sharecard.domain.model

/** Quote and bibliographic details used to create a locally generated share card. */
data class QuoteCard(
    val text: String,
    val bookTitle: String,
    val author: String,
    val bookId: String? = null,
    val locatorJson: String? = null,
    val themeId: QuoteCardThemeId = QuoteCardThemeId.PAPER,
    val format: QuoteCardFormat = QuoteCardFormat.STORY,
    val font: QuoteCardFont = QuoteCardFont.TEMPLATE,
)

/** Output proportions supported by the first version of the card editor. */
enum class QuoteCardFormat(val widthPx: Int, val heightPx: Int) {
    STORY(widthPx = 1080, heightPx = 1920),
    SQUARE(widthPx = 1080, heightPx = 1080),
}

/** Optional system font overrides; no font downloads are needed. */
enum class QuoteCardFont { TEMPLATE, SERIF, SANS_SERIF }
