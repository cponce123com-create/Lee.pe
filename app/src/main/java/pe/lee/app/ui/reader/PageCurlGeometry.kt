@file:Suppress("MagicNumber")

package pe.lee.app.ui.reader

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val PAGE_CURL_COLUMNS = 28
internal const val PAGE_CURL_ROWS = 4

/** Maps the page onto a flat surface, a cylindrical bend and the returning tail. */
internal fun fillPageCurlMesh(
    vertices: FloatArray,
    back: BooleanArray,
    width: Float,
    height: Float,
    progress: Float,
    forward: Boolean,
) {
    val p = progress.coerceIn(0f, 1f)
    val radius = width * (.07f + .05f * sin(PI.toFloat() * p))
    val retreat = width * ((p - .75f).coerceAtLeast(0f) * 2f)
    for (row in 0..PAGE_CURL_ROWS) {
        val v = row.toFloat() / PAGE_CURL_ROWS
        val fold = width * (1f - p * (1.03f + .16f * v))
        for (column in 0..PAGE_CURL_COLUMNS) {
            val u = column.toFloat() / PAGE_CURL_COLUMNS
            val x = width * if (forward) u else 1f - u
            val distance = (x - fold).coerceAtLeast(0f)
            val angle = (distance / radius).coerceAtMost(PI.toFloat())
            val depth = radius * (1f - cos(angle))
            val tail = (distance - radius * PI.toFloat()).coerceAtLeast(0f)
            val projected = minOf(x, fold) + radius * sin(angle) - tail - retreat
            val perspective = 1f + depth / (width * 3.5f)
            val offset = (row * (PAGE_CURL_COLUMNS + 1) + column) * 2
            vertices[offset] = if (forward) projected else width - projected
            vertices[offset + 1] = height * (.5f + (v - .5f) * perspective)
            back[offset / 2] = angle > PI.toFloat() / 2f
        }
    }
}

internal fun shouldCommitPageCurl(progress: Float, towardDestinationVelocity: Float, width: Int): Boolean =
    progress >= .35f || (progress >= .12f && towardDestinationVelocity >= width * 1.4f)
