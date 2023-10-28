package de.mm20.launcher2.appshortcuts

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.search.AppShortcut


fun AppShortcut(context: Context, pinRequestIntent: Intent): AppShortcut? {
    return LauncherShortcut.fromPinRequestIntent(context, pinRequestIntent)
        ?: LegacyShortcut.fromPinRequestIntent(context, pinRequestIntent)
}