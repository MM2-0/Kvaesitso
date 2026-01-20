package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.Composable

@Composable
fun LauncherBottomSheets() {
    val bottomSheetManager = LocalBottomSheetManager.current
    CustomizeSearchableSheet(
        searchable = bottomSheetManager.customizeSearchableSheetShown.value,
        onDismiss = { bottomSheetManager.dismissCustomizeSearchableModal() })
    EditFavoritesSheet(
        expanded = bottomSheetManager.editFavoritesSheetShown.value,
        onDismiss = { bottomSheetManager.dismissEditFavoritesSheet() })
    bottomSheetManager.editTagSheetShown.value?.let {
        EditTagSheet(tag = it, onDismiss = { bottomSheetManager.dismissEditTagSheet() })
    }
    FailedGestureSheet(
        bottomSheetManager.failedGestureSheetShown.value,
        onDismiss = { bottomSheetManager.dismissFailedGestureSheet() }
    )
}