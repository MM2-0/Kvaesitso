package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.Composable

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
    if (bottomSheetManager.widgetPickerSheetShown.value) {
        WidgetPickerSheet(
            onDismiss = { bottomSheetManager.dismissWidgetPickerSheet() }
        )
    }
}