package de.mm20.launcher2.ui.launcher.sheets

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import de.mm20.launcher2.search.SavableSearchable

class LauncherBottomSheetManager {
    val customizeSearchableSheetShown = mutableStateOf<SavableSearchable?>(null)
    val editFavoritesSheetShown = mutableStateOf(false)
    val hiddenItemsSheetShown = mutableStateOf(false)

    fun showCustomizeSearchableModal(item: SavableSearchable) {
        customizeSearchableSheetShown.value = item
    }
    fun dismissCustomizeSearchableModal() {
        customizeSearchableSheetShown.value = null
    }

    fun showEditFavoritesSheet() {
        editFavoritesSheetShown.value = true
    }
    fun dismissEditFavoritesSheet() {
        editFavoritesSheetShown.value = false
    }

    fun showHiddenItemsSheet() {
        hiddenItemsSheetShown.value = true
    }
    fun dismissHiddenItemsSheet() {
        hiddenItemsSheetShown.value = false
    }
}

val LocalBottomSheetManager = compositionLocalOf { LauncherBottomSheetManager() }