package de.mm20.launcher2.ui.launcher.modals

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HiddenItemsSheetVM: ViewModel() {

    fun showHiddenItems(context: Context) {
        context.startActivity(
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_ROUTE, "settings/search/hiddenitems")
            }
        )
    }
}