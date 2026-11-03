package pe.lee.feature.sharecard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId

@Composable
internal fun ThemeSelector(
    selected: QuoteCardThemeId,
    onThemeSelected: (QuoteCardThemeId) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuoteCardThemeId.entries.forEach { theme ->
            val label = when (theme) {
                QuoteCardThemeId.PAPER -> stringResource(R.string.share_card_theme_paper)
                QuoteCardThemeId.DARK_OLED -> stringResource(R.string.share_card_theme_night)
                QuoteCardThemeId.ACCENT -> stringResource(R.string.share_card_theme_accent)
            }
            FilterChip(
                selected = selected == theme,
                onClick = { onThemeSelected(theme) },
                label = { Text(label) },
                modifier = Modifier.semantics { role = Role.RadioButton }
            )
        }
    }
}
