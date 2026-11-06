@file:Suppress("MagicNumber")

package pe.lee.app.ui.reader

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun EpubPageTurnOverlay(frame: EpubPageTurnFrame, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().clipToBounds().drawWithCache {
            val count = (PAGE_CURL_COLUMNS + 1) * (PAGE_CURL_ROWS + 1)
            val vertices = FloatArray(count * 2)
            val back = BooleanArray(count)
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = frame.paperColor
                alpha = 230
            }
            val shadowPaint = Paint().apply {
                shader = LinearGradient(0f, 0f, 1f, 0f, 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            }
            val reverse = Path()
            val slideBounds = RectF()
            onDrawBehind {
                if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
                val p = frame.progress.floatValue
                if (frame.style == PageTurnStyle.SLIDE) {
                    val offset = size.width * p * if (frame.isForward) -1f else 1f
                    slideBounds.set(offset, 0f, offset + size.width, size.height)
                    drawIntoCanvas {
                        if (frame.phase.value != PageTurnPhase.COMPLETING) it.nativeCanvas.drawColor(frame.paperColor)
                        it.nativeCanvas.drawBitmap(frame.bitmap, null, slideBounds, bitmapPaint)
                    }
                    return@onDrawBehind
                }
                fillPageCurlMesh(vertices, back, size.width, size.height, p, frame.isForward)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    if (frame.phase.value != PageTurnPhase.COMPLETING) native.drawColor(frame.paperColor)
                    val strength = sin(PI.toFloat() * p).coerceIn(0f, 1f)
                    shadowPaint.alpha = (strength * 76).toInt()
                    val fold = size.width * (1f - p * 1.10f)
                    native.save()
                    native.translate(if (frame.isForward) fold else size.width - fold, 0f)
                    native.scale(size.width * .25f * if (frame.isForward) 1f else -1f, 1f)
                    native.drawRect(0f, 0f, 1f, size.height, shadowPaint)
                    native.restore()
                    native.drawBitmapMesh(frame.bitmap, PAGE_CURL_COLUMNS, PAGE_CURL_ROWS, vertices, 0, null, 0, bitmapPaint)
                    reverse.rewind()
                    for (row in 0 until PAGE_CURL_ROWS) {
                        for (column in 0 until PAGE_CURL_COLUMNS) {
                            val i = row * (PAGE_CURL_COLUMNS + 1) + column
                            val j = i + PAGE_CURL_COLUMNS + 1
                            if (back[i] && back[i + 1] && back[j] && back[j + 1]) {
                                reverse.moveTo(vertices[i * 2], vertices[i * 2 + 1])
                                reverse.lineTo(vertices[(i + 1) * 2], vertices[(i + 1) * 2 + 1])
                                reverse.lineTo(vertices[(j + 1) * 2], vertices[(j + 1) * 2 + 1])
                                reverse.lineTo(vertices[j * 2], vertices[j * 2 + 1])
                                reverse.close()
                            }
                        }
                    }
                    native.drawPath(reverse, paperPaint)
                }
            }
        },
    )
}
