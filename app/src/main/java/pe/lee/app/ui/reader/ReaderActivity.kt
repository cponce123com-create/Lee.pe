package pe.lee.app.ui.reader

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.lee.app.data.local.LeeDatabase
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
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
        fun preferencesPreferenceKey(uri: Uri): String = "epub-preferences:$uri"
        fun brightnessPreferenceKey(uri: Uri): String = "reader-brightness:$uri"
        fun keepScreenOnPreferenceKey(uri: Uri): String = "keep-screen-on:$uri"
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
    val bookDao = remember(context) { LeeDatabase.get(context).bookDao() }
    val activity = context as? FragmentActivity ?: return
    val containerId = remember { View.generateViewId() }
    val coroutineScope = rememberCoroutineScope()

    var publication by remember { mutableStateOf<Publication?>(null) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var epubPreferences by remember { mutableStateOf(EpubPreferences()) }
    var readerBrightness by remember(uri) {
        mutableFloatStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(ReaderActivity.brightnessPreferenceKey(uri), -1f))
    }
    var keepScreenOn by remember(uri) {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(ReaderActivity.keepScreenOnPreferenceKey(uri), false))
    }
    var chromeVisible by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(activity, uri) {
        val originalBrightness = activity.window.attributes.screenBrightness
        val originallyKeptAwake = activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        onDispose {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
            if (originallyKeptAwake) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(keepScreenOn) {
        if (keepScreenOn) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LaunchedEffect(readerBrightness) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = readerBrightness }
    }

    LaunchedEffect(uri, containerId) {
        try {
            val opened = openPublication()
            publication = opened

            val savedBook = bookDao.findByUri(uri.toString())
            bookDao.markOpened(uri.toString(), System.currentTimeMillis())
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val legacyLocatorJson = preferences.getString(ReaderActivity.locatorPreferenceKey(uri), null)
            val locatorJson = savedBook?.epubLocator ?: legacyLocatorJson
            val initialLocator = locatorJson?.let {
                runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
            }
            val preferencesJson = preferences.getString(ReaderActivity.preferencesPreferenceKey(uri), null)
            epubPreferences = preferencesJson?.let { json ->
                runCatching { EpubPreferencesSerializer().deserialize(json) }.getOrNull()
            } ?: EpubPreferences()
            progress = (
                initialLocator?.locations?.totalProgression
                    ?: savedBook?.readProgress?.toDouble()
                    ?: 0.0
                ).toFloat().coerceIn(0f, 1f)

            if (savedBook?.epubLocator.isNullOrBlank() && legacyLocatorJson != null && initialLocator != null) {
                bookDao.saveEpubProgress(
                    uri = uri.toString(),
                    locatorJson = legacyLocatorJson,
                    progress = progress,
                    openedAt = System.currentTimeMillis()
                )
            }

            val factory = EpubNavigatorFactory(opened)
            activity.supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = epubPreferences
            )
            activity.supportFragmentManager.commitNow {
                setReorderingAllowed(true)
                replace(containerId, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
            val epubNavigator = activity.supportFragmentManager
                .findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
                ?: error("No se pudo crear el lector EPUB.")
            epubNavigator.addInputListener(
                DirectionalNavigationAdapter(epubNavigator, animatedTransition = true)
            )
            epubNavigator.addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    chromeVisible = !chromeVisible
                    return true
                }
            })
            navigator = epubNavigator

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
            val locatorJson = locator.toJSON().toString()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(ReaderActivity.locatorPreferenceKey(uri), locatorJson)
                .apply()
            bookDao.saveEpubProgress(
                uri = uri.toString(),
                locatorJson = locatorJson,
                progress = totalProgression?.toFloat() ?: progress,
                openedAt = System.currentTimeMillis()
            )
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
            if (chromeVisible) ReaderChrome(
                title = title,
                progress = progress,
                preferences = epubPreferences,
                brightness = readerBrightness,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = { enabled ->
                    keepScreenOn = enabled
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putBoolean(ReaderActivity.keepScreenOnPreferenceKey(uri), enabled).apply()
                },
                onBrightnessChange = { brightness ->
                    readerBrightness = brightness
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putFloat(ReaderActivity.brightnessPreferenceKey(uri), brightness).apply()
                },
                onPreferencesChange = { updated ->
                    epubPreferences = updated
                    navigator?.submitPreferences(updated)
                    val serialized = runCatching {
                        EpubPreferencesSerializer().serialize(updated)
                    }.getOrNull()
                    if (serialized != null) {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(ReaderActivity.preferencesPreferenceKey(uri), serialized)
                            .apply()
                    }
                },
                onToggleChrome = { chromeVisible = false },
                onBack = onBack,
                onPrevious = { navigator?.goBackward(animated = true) },
                onNext = { navigator?.goForward(animated = true) },
                contents = publication?.let { flattenContents(it.tableOfContents) }.orEmpty(),
                onSelectContent = { link -> navigator?.go(link, animated = true) },
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
    preferences: EpubPreferences,
    brightness: Float,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit,
    onToggleChrome: () -> Unit,
    contents: List<TocEntry>,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectContent: (Link) -> Unit,
    onSeek: (Float) -> Unit,
    onProgressChange: (Float) -> Unit
) {
    var showContents by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var targetPercent by remember { mutableStateOf("") }
    val targetProgress = targetPercent.replace(',', '.').toFloatOrNull()
        ?.takeIf { it in 0f..100f }

    if (showAppearance) {
        ModalBottomSheet(onDismissRequest = { showAppearance = false }) {
            EpubAppearanceSheet(
                preferences = preferences,
                brightness = brightness,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onBrightnessChange = onBrightnessChange,
                onPreferencesChange = onPreferencesChange
            )
        }
    }

    if (showGoTo) {
        AlertDialog(
            onDismissRequest = { showGoTo = false },
            title = { Text("Ir a una posición") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Indica el porcentaje del libro. La paginación EPUB puede cambiar con la letra y la pantalla."
                    )
                    OutlinedTextField(
                        value = targetPercent,
                        onValueChange = { input ->
                            targetPercent = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text("Progreso") },
                        suffix = { Text("%") },
                        singleLine = true,
                        isError = targetProgress == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = targetProgress != null,
                    onClick = {
                        targetProgress?.let { value ->
                            val normalized = value / 100f
                            onProgressChange(normalized)
                            onSeek(normalized)
                            showGoTo = false
                        }
                    }
                ) { Text("Ir") }
            },
            dismissButton = {
                TextButton(onClick = { showGoTo = false }) { Text("Cancelar") }
            }
        )
    }

    if (showContents) {
        ModalBottomSheet(onDismissRequest = { showContents = false }) {
            Text(
                "Contenido del libro",
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (contents.isEmpty()) {
                Text(
                    "Este EPUB no incluye una tabla de contenidos.",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(contents) { entry ->
                        Text(
                            text = entry.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectContent(entry.link)
                                    showContents = false
                                }
                                .padding(
                                    start = (22 + entry.depth * 16).dp,
                                    end = 20.dp,
                                    top = 13.dp,
                                    bottom = 13.dp
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

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
                IconButton(onClick = { showAppearance = true }) {
                    Icon(Icons.Default.TextFields, contentDescription = "Apariencia de lectura", tint = Color.White)
                }
                IconButton(onClick = { showContents = true }) {
                    Icon(Icons.Default.FormatListBulleted, contentDescription = "Índice del libro", tint = Color.White)
                }
                IconButton(onClick = onToggleChrome) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = "Ocultar controles", tint = Color.White)
                }
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
                    TextButton(
                        onClick = {
                            targetPercent = (progress * 100).toInt().toString()
                            showGoTo = true
                        }
                    ) {
                        Text(
                            "Ir a ${(progress * 100).toInt()}%",
                            color = Color(0xFFD3DCEC),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Página siguiente", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubAppearanceSheet(
    preferences: EpubPreferences,
    brightness: Float,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit
) {
    var fontSize by remember(preferences.fontSize) {
        mutableFloatStateOf((preferences.fontSize ?: 1.0).toFloat().coerceIn(0.75f, 1.75f))
    }
    var lineHeight by remember(preferences.lineHeight) {
        mutableFloatStateOf((preferences.lineHeight ?: 1.2).toFloat().coerceIn(1f, 2f))
    }
    var pageMargins by remember(preferences.pageMargins) {
        mutableFloatStateOf((preferences.pageMargins ?: 1.0).toFloat().coerceIn(0.5f, 2f))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Apariencia de lectura", fontSize = 21.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tema", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Claro", "Sepia", "Oscuro", "OLED").forEach { label ->
                    FilterChip(
                        selected = selectedReaderTheme(preferences) == label,
                        onClick = {
                            val updated = when (label) {
                                "Sepia" -> preferences.copy(theme = ReadiumTheme.SEPIA, backgroundColor = null, textColor = null)
                                "Oscuro" -> preferences.copy(theme = ReadiumTheme.DARK, backgroundColor = null, textColor = null)
                                "OLED" -> preferences.copy(
                                    theme = ReadiumTheme.DARK,
                                    backgroundColor = ReadiumColor(0xFF000000.toInt()),
                                    textColor = ReadiumColor(0xFFFFFFFF.toInt())
                                )
                                else -> preferences.copy(theme = ReadiumTheme.LIGHT, backgroundColor = null, textColor = null)
                            }
                            onPreferencesChange(updated)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tamaño de letra · ${(fontSize * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(fontSize = fontSize.toDouble()))
                },
                valueRange = 0.75f..1.75f
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estilo del texto", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferences.publisherStyles != false,
                    onClick = { onPreferencesChange(preferences.copy(publisherStyles = true)) },
                    label = { Text("Estilo del libro") }
                )
                FilterChip(
                    selected = preferences.publisherStyles == false,
                    onClick = { onPreferencesChange(preferences.copy(publisherStyles = false)) },
                    label = { Text("Ajustes Lee.pe") }
                )
            }
            Text(
                "Interlineado y alineación necesitan los ajustes Lee.pe para reemplazar el formato definido por el EPUB.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Interlineado · ${(lineHeight * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = lineHeight,
                onValueChange = { lineHeight = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(lineHeight = lineHeight.toDouble(), publisherStyles = false))
                },
                valueRange = 1f..2f,
                steps = 9
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Márgenes laterales · ${(pageMargins * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = pageMargins,
                onValueChange = { pageMargins = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(pageMargins = pageMargins.toDouble()))
                },
                valueRange = 0.5f..2f,
                steps = 14
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alineación", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Inicio" to ReadiumTextAlign.START,
                    "Justificada" to ReadiumTextAlign.JUSTIFY,
                    "Centro" to ReadiumTextAlign.CENTER
                ).forEach { (label, alignment) ->
                    FilterChip(
                        selected = (preferences.textAlign ?: ReadiumTextAlign.START) == alignment,
                        onClick = {
                            onPreferencesChange(preferences.copy(textAlign = alignment, publisherStyles = false))
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Navegación", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferences.scroll != true,
                    onClick = { onPreferencesChange(preferences.copy(scroll = false)) },
                    label = { Text("Por páginas") }
                )
                FilterChip(
                    selected = preferences.scroll == true,
                    onClick = { onPreferencesChange(preferences.copy(scroll = true)) },
                    label = { Text("Desplazamiento") }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Durante la lectura", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mantener pantalla encendida")
                    Text("Evita que se apague mientras lees.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Brillo de pantalla", fontWeight = FontWeight.SemiBold)
            FilterChip(
                selected = brightness < 0f,
                onClick = { onBrightnessChange(-1f) },
                label = { Text("Usar brillo del sistema") }
            )
            Slider(
                value = if (brightness < 0f) 0.7f else brightness.coerceIn(0.15f, 1f),
                onValueChange = onBrightnessChange,
                valueRange = 0.15f..1f,
                enabled = brightness >= 0f
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tipografía", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Original" to null,
                    "Serif" to ReadiumFontFamily.SERIF,
                    "Sans" to ReadiumFontFamily.SANS_SERIF,
                    "Dislexia" to ReadiumFontFamily.OPEN_DYSLEXIC
                ).forEach { (label, family) ->
                    FilterChip(
                        selected = preferences.fontFamily?.name == family?.name,
                        onClick = { onPreferencesChange(preferences.copy(fontFamily = family)) },
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun selectedReaderTheme(preferences: EpubPreferences): String = when {
    preferences.theme == ReadiumTheme.DARK &&
        preferences.backgroundColor?.int == 0xFF000000.toInt() -> "OLED"
    preferences.theme == ReadiumTheme.SEPIA -> "Sepia"
    preferences.theme == ReadiumTheme.DARK -> "Oscuro"
    else -> "Claro"
}

private const val PREFS_NAME = "lee_pe_library"
private const val NAVIGATOR_TAG = "lee_pe_epub_navigator"

private data class TocEntry(
    val title: String,
    val link: Link,
    val depth: Int
)

private fun flattenContents(links: List<Link>, depth: Int = 0): List<TocEntry> =
    buildList {
        links.forEach { link ->
            add(TocEntry(link.title?.takeIf { it.isNotBlank() } ?: link.href.toString(), link, depth))
            addAll(flattenContents(link.children, depth + 1))
        }
    }
