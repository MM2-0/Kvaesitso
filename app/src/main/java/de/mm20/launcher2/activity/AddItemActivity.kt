package de.mm20.launcher2.activity

import android.app.Activity
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.LauncherShortcut
import org.koin.android.ext.android.inject

class AddItemActivity : Activity() {

    val favoritesRepository: FavoritesRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shortcut = AppShortcut.fromPinRequestIntent(this, intent)
        if (shortcut != null) {
            favoritesRepository.pinItem(shortcut)
        }
        finish()
    }
}
