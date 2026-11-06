package pe.lee.app.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val BookFileExtension = Regex("\\.(epub|pdf|mobi|azw3|txt|fb2|cbz|cbr)$", RegexOption.IGNORE_CASE)
private val BookTitleSpaces = Regex("\\s+")

/** Display only: the source filename and persisted book title are never renamed. */
internal fun bookSpineTitle(title: String): String = title
    .replace(BookFileExtension, "")
    .replace('_', ' ')
    .replace(BookTitleSpaces, " ")
    .trim()

private val Paper = Color(0xFFFFF0D0)
private val PaperLine = Color(0xFFB7A27B)
private val Foil = Color(0xFFE7CB91)

private enum class BindingMaterial { LEATHER, CLOTH, SMOOTH, VINTAGE }

private fun bindingMaterial(id: String): BindingMaterial = BindingMaterial.entries[(id.hashCode() ushr 2) % BindingMaterial.entries.size]

private const val MIN_TRIM_BAND_EDGES = 6
private const val BEVEL_HIGHLIGHT = .23f
private const val BEVEL_SHADE = .32f
private val SpineInkShadow = Color(0x99000000)

/** Cached grain and filigree use the same warm light direction at either orientation. */
@Suppress("CyclomaticComplexMethod")
private fun Modifier.bindingFace(base: Color, material: BindingMaterial, upright: Boolean) = drawWithCache {
    val radius = 4.dp.toPx()
    val brushColors = listOf(lerp(base, Color.Black, .28f), lerp(base, Color.White, .17f), base, lerp(base, Color.Black, .48f))
    val binding = if (upright) Brush.horizontalGradient(brushColors) else Brush.verticalGradient(brushColors)
    val grain = Path()
    if (material != BindingMaterial.SMOOTH) {
        val random = Random(37)
        val count = (size.width * size.height / (3.dp.toPx() * 3.dp.toPx())).toInt().coerceIn(0, 900)
        repeat(count) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            grain.moveTo(x, y)
            grain.lineTo(x + if (material == BindingMaterial.CLOTH) 1.8.dp.toPx() else .65.dp.toPx(), y + .35.dp.toPx())
        }
    }
    val edge = 3.dp.toPx()
    val band = if (upright) size.height else size.width
    val cross = if (upright) size.width else size.height
    fun point(along: Float, across: Float) = if (upright) Offset(across, along) else Offset(along, across)
    val trim = Path()
    val filigree = Path()
    if (material != BindingMaterial.SMOOTH && cross > 2 * edge && band > MIN_TRIM_BAND_EDGES * edge) {
        for (end in listOf(edge * 2, band - edge * 2)) {
            for (line in listOf(end - edge, end + edge)) {
                val start = point(line, edge)
                val finish = point(line, cross - edge)
                trim.moveTo(start.x, start.y)
                trim.lineTo(finish.x, finish.y)
            }
            if (material != BindingMaterial.CLOTH) {
                for (petal in -1..1) {
                    val center = cross / 2f + petal * cross / 5f
                    val start = point(end, center - cross / 10f)
                    val control = point(end - edge, center)
                    val finish = point(end, center + cross / 10f)
                    val lower = point(end + edge, center)
                    filigree.moveTo(start.x, start.y)
                    filigree.quadraticTo(control.x, control.y, finish.x, finish.y)
                    filigree.quadraticTo(lower.x, lower.y, start.x, start.y)
                }
            }
        }
    }
    val fine = androidx.compose.ui.graphics.drawscope.Stroke(.55.dp.toPx())
    onDrawBehind {
        drawRoundRect(binding, cornerRadius = CornerRadius(radius))
        drawPath(grain, Color.White.copy(alpha = .16f), style = fine)
        drawPath(trim, Foil.copy(alpha = .9f), style = fine)
        drawPath(filigree, Foil.copy(alpha = .8f), style = fine)
        if (size.width > edge * 2 && size.height > edge * 2) {
            drawRoundRect(
                Foil.copy(alpha = .24f),
                topLeft = Offset(edge, edge),
                size = Size(size.width - edge * 2, size.height - edge * 2),
                cornerRadius = CornerRadius(radius),
                style = fine,
            )
        }
    }
}

@Composable
internal fun UprightBookVolume(book: LocalBook, base: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.shadow(4.dp, RoundedCornerShape(3.dp))) {
        val depth = minOf(6.dp, maxWidth * .17f)
        val top = minOf(8.dp, maxHeight * .08f)
        val faceWidth = maxWidth - depth
        val faceHeight = maxHeight - top
        val material = bindingMaterial(book.id)
        Canvas(Modifier.fillMaxSize()) {
            val d = depth.toPx()
            val t = top.toPx()
            drawPath(
                Path().apply {
                    moveTo(size.width - d, t)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height - t / 2)
                    lineTo(size.width - d, size.height)
                    close()
                },
                lerp(base, Color.Black, .48f),
            )
            drawPath(
                Path().apply {
                    moveTo(0f, t)
                    lineTo(d, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width - d, t)
                    close()
                },
                Paper,
            )
            repeat(5) { line ->
                val fraction = (line + 1) / 6f
                val y = t * fraction
                drawLine(PaperLine.copy(alpha = .7f), Offset(d * (1 - fraction), y), Offset(size.width - d * fraction, y), .45.dp.toPx())
            }
        }
        Box(
            Modifier.offset(y = top).size(faceWidth, faceHeight)
                .bindingFace(base, material, upright = true),
        ) {
            RotatedSpineTitle(
                bookSpineTitle(book.title),
                Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 14.dp),
                material,
            )
        }
    }
}

/** Measure the label along the actual book height, then rotate it inside its spine. */
@Composable
private fun RotatedSpineTitle(title: String, modifier: Modifier, material: BindingMaterial) {
    Box(modifier) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                SpineLabel(title, material, Modifier.graphicsLayer { rotationZ = 270f }, maxLines = 3)
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val label = measurables.single().measure(Constraints(minWidth = height, maxWidth = height, maxHeight = width))
            layout(width, height) { label.place((width - label.width) / 2, (height - label.height) / 2) }
        }
    }
}

@Composable
internal fun StackedBookVolume(book: LocalBook, base: Color, flatTop: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val depth = if (flatTop) minOf(6.dp, maxHeight * .18f) else 1.dp
        val spineHeight = (maxHeight - depth).coerceAtLeast(0.dp)
        val material = bindingMaterial(book.id)
        Canvas(Modifier.fillMaxWidth().height(depth)) {
            val bevel = minOf(size.height, size.width / 4f)
            drawPath(
                Path().apply {
                    moveTo(0f, size.height)
                    lineTo(bevel, 0f)
                    lineTo(size.width - bevel, 0f)
                    lineTo(size.width, size.height)
                    close()
                },
                Brush.verticalGradient(listOf(lerp(base, Color.White, BEVEL_HIGHLIGHT), lerp(base, Color.Black, BEVEL_SHADE))),
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(spineHeight)
                .shadow(3.dp, RoundedCornerShape(4.dp))
                .bindingFace(base, material, upright = false),
            contentAlignment = Alignment.Center,
        ) {
            SpineLabel(
                bookSpineTitle(book.title),
                material,
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun SpineLabel(title: String, material: BindingMaterial, modifier: Modifier, maxLines: Int) {
    BasicText(
        text = title,
        modifier = modifier.clearAndSetSemantics {},
        style = TextStyle(
            color = Color(0xFFFFF5DF),
            fontFamily = if (material == BindingMaterial.SMOOTH) FontFamily.SansSerif else FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            shadow = Shadow(SpineInkShadow, Offset(0f, 1f), 1f),
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp, stepSize = .5.sp),
    )
}

/** Visible binding, paper fore-edge and lower cover; the supplied cover remains intact. */
@Composable
internal fun CoverBookVolume(base: Color, modifier: Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.shadow(5.dp, RoundedCornerShape(3.dp))) {
        val depth = minOf(5.dp, maxWidth * .07f)
        Box(Modifier.fillMaxSize().background(lerp(base, Color.Black, .2f), RoundedCornerShape(3.dp)))
        Box(
            Modifier.fillMaxSize().padding(start = 3.dp, top = 1.dp, end = depth, bottom = 3.dp)
                .background(Color(0xFFFFFCF4), RoundedCornerShape(2.dp)),
        ) { content() }
        Canvas(Modifier.fillMaxSize()) {
            val d = depth.toPx()
            drawPath(
                Path().apply {
                    moveTo(size.width - d, 1.dp.toPx())
                    lineTo(size.width, d)
                    lineTo(size.width, size.height - 2.dp.toPx())
                    lineTo(size.width - d, size.height - 4.dp.toPx())
                    close()
                },
                Paper,
            )
            repeat(4) { line ->
                val x = size.width - d + d * (line + 1) / 5f
                drawLine(PaperLine.copy(alpha = .5f), Offset(x, d), Offset(x, size.height - 3.dp.toPx()), .35.dp.toPx())
            }
            drawLine(Color.White.copy(alpha = .3f), Offset(2.dp.toPx(), 3f), Offset(2.dp.toPx(), size.height - 2f), .7.dp.toPx())
            drawLine(lerp(base, Color.Black, .5f), Offset(2f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
        }
    }
}
