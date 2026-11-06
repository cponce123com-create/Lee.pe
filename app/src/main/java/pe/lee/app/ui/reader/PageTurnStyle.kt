package pe.lee.app.ui.reader

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import pe.lee.app.R

internal enum class PageTurnStyle(@StringRes val label: Int) {
    CURL(R.string.reader_turn_curl),
    SLIDE(R.string.reader_turn_slide),
    NONE(R.string.reader_turn_none),
}

internal const val PAGE_TURN_STYLE_KEY = "epubPageTurnStyle"

@Composable
internal fun rememberPageAnimationsAllowed(resolver: ContentResolver): Boolean {
    val key = Settings.Global.ANIMATOR_DURATION_SCALE
    val allowed = remember(resolver) { mutableStateOf(Settings.Global.getFloat(resolver, key, 1f) > 0f) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                allowed.value = Settings.Global.getFloat(resolver, key, 1f) > 0f
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        observer.onChange(false)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return allowed.value
}
