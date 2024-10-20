package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.Composable
import de.mm20.launcher2.ui.settings.tags.EditTagSheet

@Composable
fun LauncherBottomSheets() {
    val bottomSheetManager = LocalBottomSheetManager.current
    bottomSheetManager.customizeSearchableSheetShown.value?.let {
        CustomizeSearchableSheet(
            searchable = it,
            onDismiss = { bottomSheetManager.dismissCustomizeSearchableModal() })
    }
    if (bottomSheetManager.editFavoritesSheetShown.value) {
        EditFavoritesSheet(onDismiss = { bottomSheetManager.dismissEditFavoritesSheet() })
    }
    bottomSheetManager.editTagSheetShown.value?.let {
        EditTagSheet(tag = it, onDismiss = { bottomSheetManager.dismissEditTagSheet() })
    }
}