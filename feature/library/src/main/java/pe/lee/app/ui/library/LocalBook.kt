package pe.lee.app.ui.library

import android.net.Uri

internal data class LocalBook(
    val id: String,
    val uri: Uri,
    val title: String,
    val format: String,
    val isFavorite: Boolean,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val readProgress: Float,
    val collectionId: String?,
    val offlineCopyUri: Uri?,
    val collectionNames: List<String> = listOfNotNull(collectionId)
)

