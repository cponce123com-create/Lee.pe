package pe.lee.core.domain.library

import pe.lee.core.domain.ReadingProgress

/** Validates progress before invoking a persistence operation. */
class UpdateBookProgressUseCase {
    /** Invalid/NaN progress becomes zero; finite values are clamped to [0, 1]. */
    suspend operator fun invoke(progress: Float, save: suspend (Float) -> Unit) {
        save(ReadingProgress.from(progress).fraction)
    }
}
