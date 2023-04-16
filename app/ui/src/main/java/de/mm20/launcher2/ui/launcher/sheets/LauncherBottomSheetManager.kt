package de.mm20.launcher2.ui.launcher.sheets

import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import de.mm20.launcher2.search.SavableSearchable

class LauncherBottomSheetManager(registryOwner: SavedStateRegistryOwner) :
    SavedStateRegistry.SavedStateProvider {
    val customizeSearchableSheetShown = mutableStateOf<SavableSearchable?>(null)
    val editFavoritesSheetShown = mutableStateOf(false)
    val hiddenItemsSheetShown = mutableStateOf(false)

    init {
        registryOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_CREATE) {
                val registry = registryOwner.savedStateRegistry

                registry.registerSavedStateProvider(PROVIDER, this)

                val state = registry.consumeRestoredStateForKey(PROVIDER)

                editFavoritesSheetShown.value = state?.getBoolean(FAVORITES) ?: false
                hiddenItemsSheetShown.value = state?.getBoolean(HIDDEN) ?: false
            }
        })
    }

    override fun saveState(): Bundle {
        return bundleOf(
            FAVORITES to editFavoritesSheetShown.value,
            HIDDEN to hiddenItemsSheetShown.value,
        )
    }

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

    companion object {
        private const val PROVIDER = "bottom_sheet_manager"
        private const val FAVORITES = "favorites"
        private const val HIDDEN = "hidden"
        private const val WIDGETS = "widgets"

    }
}

val LocalBottomSheetManager = staticCompositionLocalOf<LauncherBottomSheetManager> { throw IllegalStateException("No BottomSheetManager provided") }