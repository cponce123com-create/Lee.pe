package pe.lee.app.ui.reader

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pe.lee.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.getOrElse
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
internal fun EpubSearchSheet(
    searchPublication: Publication?,
    searchQuery: MutableState<String>,
    searchResults: MutableState<List<Locator>>,
    hasSearched: MutableState<Boolean>,
    searching: MutableState<Boolean>,
    searchError: MutableState<String?>,
    searchLimitReached: MutableState<Boolean>,
    onDismiss: () -> Unit,
    onSelectResult: (Locator) -> Unit
) {
    val searchScope = rememberCoroutineScope()
    val searchNotSupportedMessage = stringResource(R.string.epub_search_not_supported)
    val searchFailedMessage = stringResource(R.string.epub_search_failed)
    val preparingMessage = stringResource(R.string.epub_preparing)
    ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.epub_search_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery.value,
                        onValueChange = {
                            searchQuery.value = it
                            hasSearched.value = false
                            searchResults.value = emptyList()
                            searchError.value = null
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.epub_search_hint)) },
                        singleLine = true
                    )
                    IconButton(
                        enabled = searchQuery.value.isNotBlank() && !searching.value,
                        onClick = {
                            val query = searchQuery.value.trim()
                            val targetPublication = searchPublication
                            if (query.isNotBlank() && targetPublication != null) {
                                searching.value = true
                                searchError.value = null
                                searchResults.value = emptyList()
                                searchLimitReached.value = false
                                searchScope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val iterator = targetPublication.search(
                                                query,
                                                SearchService.Options(
                                                    caseSensitive = false,
                                                    diacriticSensitive = false
                                                )
                                            ) ?: throw IOException(searchNotSupportedMessage)
                                            val locators = mutableListOf<Locator>()
                                            var pagesRead = 0
                                            var limitReached = false
                                            try {
                                                while (locators.size < 100 && pagesRead < 20) {
                                                    val page = iterator.next().getOrElse { error ->
                                                        throw IOException(searchFailedMessage)
                                                    } ?: break
                                                    pagesRead++
                                                    locators += page.locators.take(100 - locators.size)
                                                }
                                                limitReached = locators.size >= 100 ||
                                                    (iterator.resultCount?.let { it > locators.size } == true)
                                            } finally {
                                                iterator.close()
                                            }
                                            locators to limitReached
                                        }
                                        searchResults.value = result.first
                                        searchLimitReached.value = result.second
                                        hasSearched.value = true
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        searchError.value = searchFailedMessage
                                    } finally {
                                        searching.value = false
                                    }
                                }
                            } else if (targetPublication == null) {
                                searchError.value = preparingMessage
                            }
                        }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.epub_search_action))
                    }
                }
                if (searching.value) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.epub_searching), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                searchError.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!searching.value && searchResults.value.isNotEmpty()) {
                    Text(
                        if (searchLimitReached.value) {
                            stringResource(R.string.epub_search_limit_shown)
                        } else {
                            pluralStringResource(
                                R.plurals.epub_search_matches_count,
                                searchResults.value.size,
                                searchResults.value.size
                            )
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(searchResults.value) { locator ->
                            val excerpt = listOfNotNull(locator.text.before, locator.text.highlight, locator.text.after)
                                .filter(String::isNotBlank)
                                .joinToString(" ")
                                .ifBlank { "Coincidencia en el texto" }
                            val percent = locator.locations.totalProgression
                                ?.let { "${(it.coerceIn(0.0, 1.0) * 100).toInt()}%" }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onSelectResult(locator)
                                    },
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(excerpt, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    percent?.let {
                                        Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else if (!searching.value && searchError.value == null && hasSearched.value) {
                    Text(stringResource(R.string.epub_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!searching.value && searchError.value == null && searchQuery.value.isNotBlank()) {
                    Text(stringResource(R.string.epub_search_prompt_tap), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!searching.value && searchResults.value.isEmpty() && searchError.value == null && searchQuery.value.isBlank()) {
                    Text(stringResource(R.string.epub_search_prompt_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
}
