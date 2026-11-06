package pe.lee.feature.sharecard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pe.lee.app.ui.theme.LeePeTheme
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCardFont
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.ui.components.QuoteCardCanvas
import pe.lee.feature.sharecard.ui.components.ShareCardTopBar
import pe.lee.feature.sharecard.ui.components.ThemeSelector

/** Preview, customize, and share a quote card. */
@Composable
fun ShareCardScreen(viewModel: ShareCardViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LeePeTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val layer = rememberGraphicsLayer()
        val scope = rememberCoroutineScope()
        var editingQuote by remember { mutableStateOf(false) }
        var contentFits by remember(state.card) { mutableStateOf(false) }
        var authorFits by remember(state.card) { mutableStateOf(true) }
        var capturing by remember { mutableStateOf(false) }
        val downloadFilename = stringResource(R.string.share_card_download_filename)
        val downloadLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("image/png"),
        ) { destination ->
            if (destination != null) {
                capturing = true
                scope.launch {
                    try {
                        viewModel.downloadImage(layer.toImageBitmap(), destination.toString())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        viewModel.downloadCaptureFailed()
                    } finally {
                        capturing = false
                    }
                }
            }
        }
        val exportEnabled = contentFits &&
            state.card.text.isNotBlank() &&
            !state.status.isInProgress &&
            !capturing
        val storyFormat = state.card.format == QuoteCardFormat.STORY

        Scaffold(
            topBar = { ShareCardTopBar(onBack = onBack) },
            modifier = modifier,
            bottomBar = {
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            enabled = exportEnabled,
                            onClick = { downloadLauncher.launch(downloadFilename) },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.status.isInProgress || capturing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.share_card_download))
                            }
                        }
                        Button(
                            enabled = exportEnabled,
                            onClick = {
                                capturing = true
                                scope.launch {
                                    try {
                                        // Capture within Compose's frame-aware UI scope. The ViewModel
                                        // only handles pixel conversion and PNG encoding off the main thread.
                                        viewModel.shareImage(layer.toImageBitmap())
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        viewModel.shareCaptureFailed()
                                    } finally {
                                        capturing = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.status.isInProgress || capturing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.share_card_generating))
                            } else {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.share_card_share))
                            }
                        }
                    }
                }
            },
        ) { insets ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                QuoteCardCanvas(
                    card = state.card,
                    modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (storyFormat) .64f else .85f),
                    onFitsChanged = { contentFits = it },
                    onAuthorFitsChanged = { authorFits = it },
                    captureModifier = Modifier.drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
                )
                if (!contentFits) {
                    Text(
                        stringResource(if (authorFits) R.string.share_card_too_long else R.string.share_card_author_too_long),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(stringResource(R.string.share_card_templates_count), style = MaterialTheme.typography.titleSmall)
                ThemeSelector(
                    selected = state.card.themeId,
                    onThemeSelected = viewModel::selectTheme,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.share_card_font), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteCardFont.entries.forEach { font ->
                        val label = when (font) {
                            QuoteCardFont.TEMPLATE -> R.string.share_card_font_template
                            QuoteCardFont.SERIF -> R.string.share_card_font_serif
                            QuoteCardFont.SANS_SERIF -> R.string.share_card_font_sans
                        }
                        FilterChip(
                            selected = state.card.font == font,
                            onClick = { viewModel.selectFont(font) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }

                Text(stringResource(R.string.share_card_format), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteCardFormat.entries.forEach { format ->
                        val label = when (format) {
                            QuoteCardFormat.STORY -> R.string.share_card_format_story
                            QuoteCardFormat.SQUARE -> R.string.share_card_format_square
                            QuoteCardFormat.PORTRAIT -> R.string.share_card_format_portrait
                        }
                        FilterChip(
                            selected = state.card.format == format,
                            onClick = { viewModel.selectFormat(format) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }

                Button(
                    onClick = { editingQuote = !editingQuote },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.share_card_edit_quote))
                }
                if (editingQuote) {
                    OutlinedTextField(
                        value = state.card.text,
                        onValueChange = viewModel::updateText,
                        label = { Text(stringResource(R.string.share_card_quote_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    OutlinedTextField(
                        value = state.card.author,
                        onValueChange = viewModel::updateAuthor,
                        label = { Text(stringResource(R.string.share_card_author_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                when (val status = state.status) {
                    ShareCardStatus.Idle, ShareCardStatus.Generating -> Unit
                    ShareCardStatus.Downloading -> Text(
                        stringResource(R.string.share_card_downloading),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    ShareCardStatus.Ready -> Text(
                        text = stringResource(R.string.share_card_shared),
                        // TalkBack must announce that the card is ready (A-19).
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    ShareCardStatus.Downloaded -> Text(
                        text = stringResource(R.string.share_card_downloaded),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    is ShareCardStatus.Error -> Text(
                        text = stringResource(status.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
