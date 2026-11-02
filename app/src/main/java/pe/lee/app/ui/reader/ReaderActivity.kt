package pe.lee.app.ui.reader

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import pe.lee.app.ui.theme.LeePeTheme
import java.io.IOException

class ReaderActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)

        // Readium restores its navigator as a Fragment. When Android recreates this activity after
        // killing the process, the in-memory Publication is gone, so return to the library safely.
        if (savedInstanceState != null) {
            finish()
            return
        }

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Lee.pe"
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LeePeTheme {
                EpubReaderScreen(
                    uri = uri,
                    title = title,
                    openPublication = { openPublication(uri) },
                    onBack = { finish() }
                )
            }
        }
    }

    private suspend fun openPublication(uri: Uri): Publication = withContext(Dispatchers.IO) {
        val url: AbsoluteUrl = uri.toAbsoluteUrl()
            ?: throw IOException("No se pudo acceder al archivo seleccionado.")

        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(contentResolver, httpClient)
        val asset = assetRetriever.retrieve(url).getOrElse {
            throw IOException("No se pudo leer el EPUB: ${it.message ?: "archivo inaccesible"}")
        }

        val parser = DefaultPublicationParser(
            context = this@ReaderActivity,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null
        )
        PublicationOpener(parser).open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            throw IOException("El archivo no parece ser un EPUB compatible: ${it.message ?: "formato inválido"}")
        }
    }

    companion object {
        const val EXTRA_TITLE = "pe.lee.app.reader.TITLE"
        private const val PREFS_NAME = "lee_pe_library"
        private const val KEY_DOCUMENT_URIS = "document_uris"

        fun locatorPreferenceKey(uri: Uri): String = "locator:$uri"
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun EpubReaderScreen(
    uri: Uri,
    title: String,
    openPublication: suspend () -> Publication,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val containerId = remember { View.generateViewId() }
    val coroutineScope = rememberCoroutineScope()

    var publication by remember { mutableStateOf<Publication?>(null) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri, containerId) {
        try {
            val opened = openPublication()
            publication = opened

            val preferences = context.getSharedPreferences(PREFS_NAME, FragmentActivity.MODE_PRIVATE)
            val locatorJson = preferences.getString(ReaderActivity.locatorPreferenceKey(uri), null)
            val initialLocator = locatorJson?.let {
                runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
            }
            progress = (initialLocator?.locations?.totalProgression ?: 0.0).toFloat()

            val factory = EpubNavigatorFactory(opened)
            activity.supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = EpubPreferences()
            )
            activity.supportFragmentManager.commitNow {
                setReorderingAllowed(true)
                replace(containerId, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
            navigator = activity.supportFragmentManager
                .findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
                ?: error("No se pudo crear el lector EPUB.")

            loading = false
        } catch (error: Exception) {
            errorMessage = error.message ?: "No se pudo abrir este EPUB."
            loading = false
        }
    }

    LaunchedEffect(navigator) {
        val reader = navigator ?: return@LaunchedEffect
        reader.currentLocator.collect { locator ->
            val totalProgression = locator.locations.totalProgression
            if (totalProgression != null) progress = totalProgression.toFloat().coerceIn(0f, 1f)
            context.getSharedPreferences(PREFS_NAME, FragmentActivity.MODE_PRIVATE)
                .edit()
                .putString(ReaderActivity.locatorPreferenceKey(uri), locator.toJSON().toString())
                .apply()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111F))
    ) {
        AndroidView(
            factory = { FrameLayout(it).apply { id = containerId } },
            modifier = Modifier.fillMaxSize()
        )

        if (loading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                Text("Abriendo tu libro…", color = Color.White)
            }
        }

        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("No se pudo abrir el libro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(message, color = Color(0xFFD1D8E5))
                Button(onClick = onBack) { Text("Volver a la biblioteca") }
            }
        }

        if (!loading && errorMessage == null) {
            ReaderChrome(
                title = title,
                progress = progress,
                onBack = onBack,
                onPrevious = { navigator?.goBackward(animated = true) },
                onNext = { navigator?.goForward(animated = true) },
                onSeek = {
                    coroutineScope.launch {
                        publication?.locateProgression(it.toDouble())?.let { locator ->
                            navigator?.go(locator)
                        }
                    }
                },
                onProgressChange = { progress = it }
            )
        }
    }
}

@Composable
private fun ReaderChrome(
    title: String,
    progress: Float,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onProgressChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            color = Color(0xD90A1423),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("EPUB", color = Color(0xFF75DFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(12.dp))
            }
        }

        Surface(
            color = Color(0xE60A1423),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Slider(
                    value = progress,
                    onValueChange = onProgressChange,
                    onValueChangeFinished = { onSeek(progress.coerceIn(0f, 1f)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Página anterior", tint = Color.White)
                    }
                    Text(
                        "${(progress * 100).toInt()}% del libro",
                        color = Color(0xFFD3DCEC),
                        fontSize = 12.sp
                    )
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Página siguiente", tint = Color.White)
                    }
                }
            }
        }
    }
}

private const val PREFS_NAME = "lee_pe_library"
private const val NAVIGATOR_TAG = "lee_pe_epub_navigator"
