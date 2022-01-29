package de.mm20.launcher2.activity;

import android.app.Activity
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.search.data.AppShortcut
import org.koin.android.ext.android.inject

class AddItemActivity : Activity() {

    val favoritesRepository: FavoritesRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val pinRequest = launcherApps.getPinItemRequest(intent) ?: return run { finish() }
        val shortcutInfo = pinRequest.shortcutInfo ?: return run { finish() }
        val shortcut = AppShortcut(
            this.applicationContext, shortcutInfo,
            packageManager.getApplicationInfo(shortcutInfo.`package`, 0)
                .loadLabel(packageManager).toString()
        )
        if (pinRequest.accept()) {
            favoritesRepository.pinItem(shortcut)
        }
        finish()
    }
}
