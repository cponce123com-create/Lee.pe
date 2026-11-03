package pe.lee.feature.sharecard.navigation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import pe.lee.feature.sharecard.di.ShareCardModule
import pe.lee.feature.sharecard.ui.ShareCardScreen
import pe.lee.feature.sharecard.ui.ShareCardViewModel

/** Internal Activity host for the share-card editor in the current Activity-based app navigation. */
class ShareCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val card = intent.readQuoteCardArgs()
        if (card.text.isBlank()) {
            finish()
            return
        }
        val viewModel = ViewModelProvider(this, ShareCardModule.viewModelFactory(this, card))
            .get(ShareCardViewModel::class.java)
        setContent { ShareCardScreen(viewModel = viewModel, onBack = ::finish) }
    }

    companion object {
        const val EXTRA_TEXT = "pe.lee.feature.sharecard.TEXT"
        const val EXTRA_BOOK_ID = "pe.lee.feature.sharecard.BOOK_ID"
        const val EXTRA_BOOK_TITLE = "pe.lee.feature.sharecard.BOOK_TITLE"
        const val EXTRA_AUTHOR = "pe.lee.feature.sharecard.AUTHOR"
        const val EXTRA_LOCATOR_JSON = "pe.lee.feature.sharecard.LOCATOR_JSON"
    }
}
