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
    EditTagSheet(
        expanded = bottomSheetManager.editTagSheetShown.value != null,
        tag = bottomSheetManager.editTagSheetShown.value,
        onDismiss = { bottomSheetManager.dismissEditTagSheet() }
    )
    FailedGestureSheet(
        bottomSheetManager.failedGestureSheetShown.value,
        onDismiss = { bottomSheetManager.dismissFailedGestureSheet() }
    )
}