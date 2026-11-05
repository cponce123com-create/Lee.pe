package pe.lee.feature.sharecard.ui.theme

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.lang.ref.SoftReference
import kotlin.math.max
import kotlin.math.roundToInt
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId

/** Original user artwork is imported intact; regions are selected only while drawing. */
internal val premiumThemes = listOf(
    QuoteCardThemeId.PAPYRUS, QuoteCardThemeId.LETTER, QuoteCardThemeId.EDITORIAL, QuoteCardThemeId.TORN_PAPER,
    QuoteCardThemeId.REAL_PARCHMENT, QuoteCardThemeId.EMBROIDERY, QuoteCardThemeId.OLD_BOOK, QuoteCardThemeId.MARBLE_GOLD,
    QuoteCardThemeId.NIGHT_SKY, QuoteCardThemeId.RAIN, QuoteCardThemeId.SUNSET, QuoteCardThemeId.CALM_SEA,
    QuoteCardThemeId.FOREST, QuoteCardThemeId.MOSS_GARDEN, QuoteCardThemeId.PREMIUM, QuoteCardThemeId.DARK_OLED,
    QuoteCardThemeId.LIBRARY, QuoteCardThemeId.BOOK_PAGE, QuoteCardThemeId.PAPER, QuoteCardThemeId.CHROME,
    QuoteCardThemeId.DRIED_FLOWERS, QuoteCardThemeId.BOTANICAL, QuoteCardThemeId.WHITE_RELIEF, QuoteCardThemeId.MINIMAL,
    QuoteCardThemeId.SAND, QuoteCardThemeId.POLAROID, QuoteCardThemeId.CYANOTYPE, QuoteCardThemeId.ACCENT,
    QuoteCardThemeId.JAPANESE_INK, QuoteCardThemeId.WATERCOLOR, QuoteCardThemeId.CLAY, QuoteCardThemeId.CARVED_STONE,
    QuoteCardThemeId.GEOMETRY, QuoteCardThemeId.AURA, QuoteCardThemeId.MAGAZINE, QuoteCardThemeId.WOOD_ENGRAVING,
    QuoteCardThemeId.AZULEJOS, QuoteCardThemeId.CRYSTAL, QuoteCardThemeId.PROTAGONIST, QuoteCardThemeId.RETRO_POSTER,
)

internal enum class QuoteMood(val labelRes: Int) {
    ALL(R.string.share_card_mood_all),
    LITERARY(R.string.share_card_mood_literary),
    CALM(R.string.share_card_mood_calm),
    REFLECTION(R.string.share_card_mood_reflection),
    MELANCHOLY(R.string.share_card_mood_melancholy),
    JOY(R.string.share_card_mood_joy),
    STRENGTH(R.string.share_card_mood_strength),
}

internal fun premiumThemeIndex(theme: QuoteCardThemeId): Int = premiumThemes.indexOf(theme).also { require(it >= 0) }
internal fun premiumLightPaper(theme: QuoteCardThemeId): Boolean = premiumThemeIndex(theme) < 6
internal fun premiumMatchesMood(theme: QuoteCardThemeId, mood: QuoteMood): Boolean {
    val index = premiumThemeIndex(theme)
    return when (mood) {
        QuoteMood.ALL -> true
        QuoteMood.LITERARY -> index in 0..7 || index in 16..19 || index == 33
        QuoteMood.CALM -> index in setOf(8, 11, 12, 13, 22, 23, 24, 37, 38, 39)
        QuoteMood.REFLECTION -> index in setOf(16, 18, 19, 20, 24, 25, 31, 35, 37)
        QuoteMood.MELANCHOLY -> index in setOf(9, 14, 15, 21, 26, 27, 28)
        QuoteMood.JOY -> index in setOf(10, 11, 12, 22, 23, 29, 33, 34, 35, 36)
        QuoteMood.STRENGTH -> index in setOf(7, 30, 31, 32, 38)
    }
}

internal data class PremiumCrop(val left: Int, val top: Int, val width: Int, val height: Int)

internal fun premiumCrop(theme: QuoteCardThemeId): PremiumCrop {
    val index = premiumThemeIndex(theme)
    val lefts = intArrayOf(14, 240, 448, 636, 820, 1009, 1199, 1377)
    val rights = intArrayOf(219, 426, 615, 798, 987, 1178, 1354, 1522)
    val tops = intArrayOf(14, 308, 600, 872, 1161)
    val bottoms = intArrayOf(285, 576, 849, 1138, 1466)
    val column = index % 8
    val row = index / 8
    return PremiumCrop(lefts[column], tops[row], rights[column] - lefts[column], bottoms[row] - tops[row])
}

private object PremiumAtlasCache {
    private var cached = SoftReference<ImageBitmap>(null)

    @Synchronized
    fun load(resources: Resources): ImageBitmap = cached.get() ?: BitmapFactory.decodeResource(
        resources, R.drawable.quote_premium_atlas,
        BitmapFactory.Options().apply { inScaled = false },
    ).asImageBitmap().also { cached = SoftReference(it) }
}

/** A single shared atlas avoids decoding forty separate full-size bitmaps. */
@Composable
internal fun PremiumArtwork(theme: QuoteCardThemeId, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val atlas = remember(resources) { PremiumAtlasCache.load(resources) }
    val crop = remember(theme) { premiumCrop(theme) }
    val sourceWidth = crop.width
    val sourceHeight = crop.height
    Canvas(modifier) {
        val scale = max(size.width / sourceWidth, size.height / sourceHeight)
        val cropWidth = (size.width / scale).roundToInt().coerceIn(1, sourceWidth)
        val cropHeight = (size.height / scale).roundToInt().coerceIn(1, sourceHeight)
        drawImage(
            atlas,
            srcOffset = IntOffset(crop.left + (sourceWidth - cropWidth) / 2, crop.top + (sourceHeight - cropHeight) / 2),
            srcSize = IntSize(cropWidth, cropHeight),
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
    }
}
