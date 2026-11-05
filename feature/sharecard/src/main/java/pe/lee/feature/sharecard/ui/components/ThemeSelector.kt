package pe.lee.feature.sharecard.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId
import pe.lee.feature.sharecard.ui.theme.PremiumArtwork
import pe.lee.feature.sharecard.ui.theme.QuoteMood
import pe.lee.feature.sharecard.ui.theme.allQuoteThemes
import pe.lee.feature.sharecard.ui.theme.premiumMatchesMood

@Composable
internal fun ThemeSelector(selected: QuoteCardThemeId, onThemeSelected: (QuoteCardThemeId) -> Unit, modifier: Modifier = Modifier) {
    var mood by remember { mutableStateOf(QuoteMood.ALL) }
    val gallery = rememberLazyListState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(QuoteMood.entries) { category ->
                FilterChip(
                    selected = mood == category,
                    onClick = { mood = category },
                    label = { Text(stringResource(category.labelRes)) },
                )
            }
        }
        LazyRow(
            state = gallery,
            modifier = Modifier.fillMaxWidth().testTag("quote-theme-gallery"),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(allQuoteThemes.filter { premiumMatchesMood(it, mood) }, key = { it.name }) { theme ->
                val chosen = selected == theme
                OutlinedCard(
                    onClick = { onThemeSelected(theme) },
                    modifier = Modifier.width(90.dp).semantics {
                        this.selected = chosen
                        role = Role.RadioButton
                    },
                    border = BorderStroke(
                        if (chosen) 2.dp else 1.dp,
                        if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    PremiumArtwork(theme, Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)))
                    Text(stringResource(theme.labelRes), Modifier.padding(6.dp), maxLines = 2, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

internal val QuoteCardThemeId.labelRes: Int
    get() = when (this) {
        QuoteCardThemeId.GOLDEN_NIGHT -> R.string.share_card_theme_golden_night
        QuoteCardThemeId.BOTANICAL_IVORY -> R.string.share_card_theme_botanical_ivory
        QuoteCardThemeId.ELEGANT_FOREST -> R.string.share_card_theme_elegant_forest
        QuoteCardThemeId.PAPER_ROSE -> R.string.share_card_theme_paper_rose
        QuoteCardThemeId.STONE_GOLD -> R.string.share_card_theme_stone_gold
        QuoteCardThemeId.LIVING_PARCHMENT -> R.string.share_card_theme_living_parchment
        QuoteCardThemeId.INK_MOON -> R.string.share_card_theme_ink_moon
        QuoteCardThemeId.LINEN_FEATHER -> R.string.share_card_theme_linen_feather
        QuoteCardThemeId.LITERARY_LEATHER -> R.string.share_card_theme_literary_leather
        QuoteCardThemeId.LIGHT_SPROUT -> R.string.share_card_theme_light_sprout

        QuoteCardThemeId.EDITORIAL -> R.string.share_card_theme_editorial
        QuoteCardThemeId.DARK_OLED -> R.string.share_card_theme_night
        QuoteCardThemeId.PAPER -> R.string.share_card_theme_paper
        QuoteCardThemeId.MINIMAL -> R.string.share_card_theme_minimal
        QuoteCardThemeId.PREMIUM -> R.string.share_card_theme_premium
        QuoteCardThemeId.ACCENT -> R.string.share_card_theme_accent
        QuoteCardThemeId.FOREST -> R.string.share_card_theme_forest
        QuoteCardThemeId.CALM_SEA -> R.string.share_card_theme_calm_sea
        QuoteCardThemeId.SUNSET -> R.string.share_card_theme_sunset
        QuoteCardThemeId.RAIN -> R.string.share_card_theme_rain
        QuoteCardThemeId.WATERCOLOR -> R.string.share_card_theme_watercolor
        QuoteCardThemeId.BOTANICAL -> R.string.share_card_theme_botanical
        QuoteCardThemeId.DRIED_FLOWERS -> R.string.share_card_theme_dried_flowers
        QuoteCardThemeId.NIGHT_SKY -> R.string.share_card_theme_night_sky
        QuoteCardThemeId.PROTAGONIST -> R.string.share_card_theme_protagonist
        QuoteCardThemeId.MAGAZINE -> R.string.share_card_theme_magazine
        QuoteCardThemeId.GEOMETRY -> R.string.share_card_theme_geometry
        QuoteCardThemeId.POLAROID -> R.string.share_card_theme_polaroid
        QuoteCardThemeId.LETTER -> R.string.share_card_theme_letter
        QuoteCardThemeId.BOOK_PAGE -> R.string.share_card_theme_book_page
        QuoteCardThemeId.LIBRARY -> R.string.share_card_theme_library
        QuoteCardThemeId.OLD_BOOK -> R.string.share_card_theme_old_book
        QuoteCardThemeId.PAPYRUS -> R.string.share_card_theme_papyrus
        QuoteCardThemeId.CARVED_STONE -> R.string.share_card_theme_carved_stone
        QuoteCardThemeId.MARBLE_GOLD -> R.string.share_card_theme_marble_gold
        QuoteCardThemeId.CLAY -> R.string.share_card_theme_clay
        QuoteCardThemeId.REAL_PARCHMENT -> R.string.share_card_theme_real_parchment
        QuoteCardThemeId.JAPANESE_INK -> R.string.share_card_theme_japanese_ink
        QuoteCardThemeId.CYANOTYPE -> R.string.share_card_theme_cyanotype
        QuoteCardThemeId.CRYSTAL -> R.string.share_card_theme_crystal
        QuoteCardThemeId.CHROME -> R.string.share_card_theme_chrome
        QuoteCardThemeId.AURA -> R.string.share_card_theme_aura
        QuoteCardThemeId.TORN_PAPER -> R.string.share_card_theme_torn_paper
        QuoteCardThemeId.WOOD_ENGRAVING -> R.string.share_card_theme_wood_engraving
        QuoteCardThemeId.EMBROIDERY -> R.string.share_card_theme_embroidery
        QuoteCardThemeId.AZULEJOS -> R.string.share_card_theme_azulejos
        QuoteCardThemeId.RETRO_POSTER -> R.string.share_card_theme_retro_poster
        QuoteCardThemeId.MOSS_GARDEN -> R.string.share_card_theme_moss_garden
        QuoteCardThemeId.SAND -> R.string.share_card_theme_sand
        QuoteCardThemeId.WHITE_RELIEF -> R.string.share_card_theme_white_relief
    }
