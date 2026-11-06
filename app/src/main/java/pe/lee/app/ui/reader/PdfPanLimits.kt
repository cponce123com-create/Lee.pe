package pe.lee.app.ui.reader

private const val HALF_TURN_DEGREES = 180

/** Layout dimensions are measured before the graphics layer rotates the fitted page. */
internal fun pdfPanLimits(
    pageWidth: Float,
    pageHeight: Float,
    layoutWidth: Float,
    layoutHeight: Float,
    rotationDegrees: Int,
    zoom: Float,
): Pair<Float, Float> {
    val fit = minOf(layoutWidth / pageWidth, layoutHeight / pageHeight)
    val horizontal = ((pageWidth * fit * zoom - layoutWidth) / 2f).coerceAtLeast(0f)
    val vertical = ((pageHeight * fit * zoom - layoutHeight) / 2f).coerceAtLeast(0f)
    return if (rotationDegrees % HALF_TURN_DEGREES == 0) horizontal to vertical else vertical to horizontal
}
