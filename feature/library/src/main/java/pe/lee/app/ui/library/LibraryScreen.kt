package pe.lee.app.ui.library

import android.net.Uri
import androidx.compose.runtime.Composable
import pe.lee.app.data.repository.LocalBackupRepository
import pe.lee.app.data.repository.SettingsRepository

/** Library route: binds lifecycle-owned state to the catalog and its dialogs. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    settings: SettingsRepository,
    backupRepository: LocalBackupRepository,
    onOpenBook: (uri: Uri, format: String, title: String, offlineCopyUri: Uri?) -> Unit,
) {
    val state = rememberLibraryScreenState(viewModel, settings, backupRepository)
    LibraryShelfTheme {
        LibraryScreenDialogs(state)
        LibraryScreenCatalog(state, onOpenBook)
    }
}
