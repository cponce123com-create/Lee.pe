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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Composable
internal fun UprightBookVolume(book: LocalBook, base: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.shadow(4.dp, RoundedCornerShape(3.dp))) {
        val depth = minOf(6.dp, maxWidth * .17f)
        val top = minOf(8.dp, maxHeight * .08f)
        val faceWidth = maxWidth - depth
        val faceHeight = maxHeight - top
        val dark = lerp(base, Color.Black, .28f)
        Canvas(Modifier.fillMaxSize()) {
            val d = depth.toPx()
            val t = top.toPx()
            drawPath(Path().apply {
                moveTo(size.width - d, t)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height - t / 2)
                lineTo(size.width - d, size.height)
                close()
            }, lerp(base, Color.Black, .48f))
            drawPath(Path().apply {
                moveTo(0f, t)
                lineTo(d, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - d, t)
                close()
            }, Paper)
            repeat(5) { line ->
                val fraction = (line + 1) / 6f
                val y = t * fraction
                drawLine(PaperLine.copy(alpha = .7f), Offset(d * (1 - fraction), y), Offset(size.width - d * fraction, y), .45.dp.toPx())
            }
        }
        Box(
            Modifier.offset(y = top).size(faceWidth, faceHeight)
                .background(
                    Brush.horizontalGradient(listOf(dark, base, lerp(base, Color.Black, .12f), dark)),
                    RoundedCornerShape(3.dp),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val seam = 3.dp.toPx()
                drawLine(Color.White.copy(alpha = .22f), Offset(seam, 2f), Offset(seam, size.height - 2f), .7.dp.toPx())
                for (y in listOf(6.dp.toPx(), size.height - 6.dp.toPx())) {
                    drawLine(Foil.copy(alpha = .65f), Offset(5.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), .6.dp.toPx())
                }
            }
            RotatedSpineTitle(bookSpineTitle(book.title), Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 11.dp))
        }
    }
}

/** Measure the label along the actual book height, then rotate it inside its spine. */
@Composable
private fun RotatedSpineTitle(title: String, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val font = if (maxWidth < 23.dp) 9.5.sp else 10.5.sp
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Text(
                    title,
                    modifier = Modifier.graphicsLayer { rotationZ = 270f }.clearAndSetSemantics {},
                    color = Color(0xFFFFF5DF), fontSize = font, lineHeight = font * 1.12f,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(shadow = Shadow(Color(0x66000000), Offset(0f, 1f), 1f)),
                )
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
        val spineHeight = if (flatTop) minOf(27.dp, maxOf(21.dp, maxHeight * .35f)) else maxHeight
        val coverHeight = (maxHeight - spineHeight).coerceAtLeast(0.dp)
        val dark = lerp(base, Color.Black, .25f)
        if (flatTop && coverHeight > 0.dp) {
            ShelfCoverArt(
                book,
                Modifier.fillMaxWidth().height(coverHeight).graphicsLayer {
                    rotationX = 32f
                    rotationY = -4f
                    cameraDistance = 24f * density
                    transformOrigin = TransformOrigin(.5f, 1f)
                },
                portrait = false,
            )
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(spineHeight)
                .shadow(3.dp, RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(base, dark, lerp(base, Color.Black, .4f))), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(Foil.copy(alpha = .7f), Offset(3f, 1f), Offset(size.width - 3f, 1f), .7.dp.toPx())
                drawLine(Color.Black.copy(alpha = .35f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
            }
            Text(
                bookSpineTitle(book.title), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp).clearAndSetSemantics {},
                color = Color(0xFFFFF5DF), fontSize = 9.5.sp, lineHeight = 10.5.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = if (spineHeight >= 23.dp) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
            drawPath(Path().apply {
                moveTo(size.width - d, 1.dp.toPx())
                lineTo(size.width, d)
                lineTo(size.width, size.height - 2.dp.toPx())
                lineTo(size.width - d, size.height - 4.dp.toPx())
                close()
            }, Paper)
            repeat(4) { line ->
                val x = size.width - d + d * (line + 1) / 5f
                drawLine(PaperLine.copy(alpha = .5f), Offset(x, d), Offset(x, size.height - 3.dp.toPx()), .35.dp.toPx())
            }
            drawLine(Color.White.copy(alpha = .3f), Offset(2.dp.toPx(), 3f), Offset(2.dp.toPx(), size.height - 2f), .7.dp.toPx())
            drawLine(lerp(base, Color.Black, .5f), Offset(2f, size.height - 1f), Offset(size.width, size.height - 1f), 1.dp.toPx())
        }
    }
}
