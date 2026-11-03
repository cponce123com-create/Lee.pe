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
        fun from(rawFraction: Float): ReadingProgress =
            ReadingProgress(if (rawFraction.isFinite()) rawFraction.coerceIn(0f, 1f) else 0f)
    }
}
