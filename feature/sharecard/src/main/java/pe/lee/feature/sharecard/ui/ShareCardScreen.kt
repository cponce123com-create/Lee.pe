package pe.lee.feature.sharecard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import pe.lee.app.ui.theme.LeePeTheme
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.ui.components.QuoteCardPreview
import pe.lee.feature.sharecard.ui.components.ShareCardTopBar
import pe.lee.feature.sharecard.ui.components.ThemeSelector

/** Preview, customize, and share a quote card. */
@Composable
fun ShareCardScreen(
    viewModel: ShareCardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LeePeTheme {
        val state by viewModel.state.collectAsState()
        val layer = rememberGraphicsLayer()
        val frameClock = requireNotNull(rememberCoroutineScope().coroutineContext[MonotonicFrameClock])
        var editingQuote by remember { mutableStateOf(false) }
        val storyFormat = state.card.format == QuoteCardFormat.STORY

        Scaffold(
            topBar = { ShareCardTopBar(onBack = onBack) },
            modifier = modifier
        ) { insets ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (storyFormat) 20.dp else 4.dp)
                        .aspectRatio(if (storyFormat) 9f / 16f else 1f)
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                ) {
                    QuoteCardPreview(card = state.card, modifier = Modifier.fillMaxSize())
                }

                Text(stringResource(R.string.share_card_design), style = MaterialTheme.typography.titleSmall)
                ThemeSelector(
                    selected = state.card.themeId,
                    onThemeSelected = viewModel::selectTheme,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.share_card_format), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = storyFormat,
                        onClick = { viewModel.selectFormat(QuoteCardFormat.STORY) },
                        label = { Text(stringResource(R.string.share_card_format_story)) }
                    )
                    FilterChip(
                        selected = !storyFormat,
                        onClick = { viewModel.selectFormat(QuoteCardFormat.SQUARE) },
                        label = { Text(stringResource(R.string.share_card_format_square)) }
                    )
                }

                Button(
                    onClick = { editingQuote = !editingQuote },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.share_card_edit_quote))
                }
                if (editingQuote) {
                    OutlinedTextField(
                        value = state.card.text,
                        onValueChange = viewModel::updateText,
                        label = { Text(stringResource(R.string.share_card_quote_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    OutlinedTextField(
                        value = state.card.bookTitle,
                        onValueChange = viewModel::updateBookTitle,
                        label = { Text(stringResource(R.string.share_card_book_title_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.card.author,
                        onValueChange = viewModel::updateAuthor,
                        label = { Text(stringResource(R.string.share_card_author_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Button(
                    enabled = state.card.text.isNotBlank() && state.status !is ShareCardStatus.Generating,
                    onClick = {
                        viewModel.shareCapturedImage {
                            // viewModelScope has no Compose frame clock; retain it for capture.
                            withContext(frameClock) {
                                withFrameNanos { }
                                layer.toImageBitmap()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.status is ShareCardStatus.Generating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share_card_generating))
                    } else {
                        Text(stringResource(R.string.share_card_share))
                    }
                }
                when (val status = state.status) {
                    ShareCardStatus.Idle, ShareCardStatus.Generating -> Unit
                    ShareCardStatus.Ready -> Text(stringResource(R.string.share_card_shared))
                    is ShareCardStatus.Error -> Text(
                        text = status.message.ifBlank { stringResource(R.string.share_card_error) },
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
