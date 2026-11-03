package pe.lee.feature.sharecard.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import pe.lee.feature.sharecard.data.ComposeShareCardGenerator
import pe.lee.feature.sharecard.data.ShareCardSharer
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.ui.ShareCardViewModel

/** Manual composition root for this feature, matching the app's current dependency setup. */
object ShareCardModule {
    /** Creates a ViewModel factory using application-scoped platform dependencies. */
    fun viewModelFactory(context: Context, card: QuoteCard): ViewModelProvider.Factory {
        val appContext = context.applicationContext
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(ShareCardViewModel::class.java)) {
                    throw IllegalArgumentException("ViewModel no compatible: ${modelClass.name}")
                }
                return ShareCardViewModel(
                    initialCard = card,
                    generator = ComposeShareCardGenerator(),
                    sharer = ShareCardSharer(appContext)
                ) as T
            }
        }
    }
}
