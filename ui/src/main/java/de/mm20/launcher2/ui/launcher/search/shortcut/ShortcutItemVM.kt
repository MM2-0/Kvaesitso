package de.mm20.launcher2.ui.launcher.search.shortcut

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM

class ShortcutItemVM(private val shortcut: AppShortcut) : SearchableItemVM(shortcut) {
    fun openAppInfo(context: AppCompatActivity) {
        context.tryStartActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${shortcut.launcherShortcut.`package`}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}