package pe.lee.feature.sharecard.navigation

import android.content.Context
import android.content.Intent
import pe.lee.feature.sharecard.domain.model.QuoteCard

/** Reader-to-share-card payload; locator JSON is retained for future return-to-selection support. */
data class ShareCardArgs(val text: String, val bookId: String, val bookTitle: String, val author: String, val locatorJson: String? = null)

/** Creates the internal feature activity intent without exposing its implementation details. */
fun shareCardIntent(context: Context, args: ShareCardArgs): Intent = Intent(context, ShareCardActivity::class.java).apply {
    putExtra(ShareCardActivity.EXTRA_TEXT, args.text)
    putExtra(ShareCardActivity.EXTRA_BOOK_ID, args.bookId)
    putExtra(ShareCardActivity.EXTRA_BOOK_TITLE, args.bookTitle)
    putExtra(ShareCardActivity.EXTRA_AUTHOR, args.author)
    putExtra(ShareCardActivity.EXTRA_LOCATOR_JSON, args.locatorJson)
}

internal fun Intent.readQuoteCardArgs(): QuoteCard = QuoteCard(
    text = getStringExtra(ShareCardActivity.EXTRA_TEXT).orEmpty(),
    bookId = getStringExtra(ShareCardActivity.EXTRA_BOOK_ID),
    bookTitle = getStringExtra(ShareCardActivity.EXTRA_BOOK_TITLE).orEmpty(),
    author = getStringExtra(ShareCardActivity.EXTRA_AUTHOR).orEmpty(),
    locatorJson = getStringExtra(ShareCardActivity.EXTRA_LOCATOR_JSON),
)
