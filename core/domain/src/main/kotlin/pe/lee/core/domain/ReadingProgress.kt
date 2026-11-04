package pe.lee.core.domain

/** A validated reading fraction that can safely be persisted and presented. */
@JvmInline
value class ReadingProgress private constructor(val fraction: Float) {
    val percentage: Int
        get() = (fraction * 100f).toInt()

    val isStarted: Boolean
        get() = fraction > 0f

    val isCompleted: Boolean
        get() = fraction >= 1f

    companion object {
        fun from(rawFraction: Float): ReadingProgress = ReadingProgress(if (rawFraction.isFinite()) rawFraction.coerceIn(0f, 1f) else 0f)
    }
}

/**
 * Progress reported by a locator, falling back to the last known value when the
 * publication cannot compute `totalProgression`.
 *
 * EPUBs without a page list or with a partial spine frequently report `null`
 * here. Persisting 0 in that case would silently reset a book that the user has
 * already read, so the previous fraction wins instead.
 */
fun readingProgressOr(progression: Double?, currentFraction: Float): Float {
    val sanitized = progression?.takeIf { it.isFinite() }?.toFloat()
    return if (sanitized != null) {
        ReadingProgress.from(sanitized).fraction
    } else {
        ReadingProgress.from(currentFraction).fraction
    }
}
