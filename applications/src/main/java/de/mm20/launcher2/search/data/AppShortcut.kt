package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.applications.R
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.IconSettings.LegacyIconBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppShortcut(
    context: Context,
    val launcherShortcut: ShortcutInfo,
    val appName: String
) : Searchable() {

    override val label: String
        get() = launcherShortcut.shortLabel?.toString() ?: ""


    internal val userSerialNumber: Long = launcherShortcut.userHandle.getSerialNumber(context)
    private val isMainProfile = launcherShortcut.userHandle == Process.myUserHandle()

    override val key: String
        get() = if (isMainProfile) {
            "shortcut://${launcherShortcut.`package`}/${launcherShortcut.id}"
        } else {
            "shortcut://${launcherShortcut.`package`}/${launcherShortcut.id}:${userSerialNumber}"
        }

    override val badgeKey: String
        get() {
            return if (isMainProfile) "shortcut://${launcherShortcut.activity?.flattenToShortString()}" else "profile://$userSerialNumber"
        }

    override fun getLaunchIntent(context: Context): Intent? {
        return launcherShortcut.intent
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        try {
            launcherApps.startShortcut(launcherShortcut, null, options)
        } catch (e: IllegalStateException) {
            return false
        }
        return true
    }

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        return LauncherIcon(
            foreground = ContextCompat.getDrawable(context, R.drawable.ic_file_android)!!,
            background = ColorDrawable(ContextCompat.getColor(context, R.color.green)),
            foregroundScale = 0.5f
        )
    }

    override suspend fun loadIcon(context: Context, size: Int, legacyIconBackground: LegacyIconBackground): LauncherIcon? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val icon = withContext(Dispatchers.IO) {
            launcherApps.getShortcutIconDrawable(
                launcherShortcut,
                context.resources.displayMetrics.densityDpi
            )
        } ?: return null
        if (icon is AdaptiveIconDrawable) {
            return LauncherIcon(
                foreground = icon.foreground,
                background = icon.background,
                foregroundScale = 1.5f,
                backgroundScale = 1.5f
            )
        }
        return LauncherIcon(
            foreground = icon,
            foregroundScale = 1f,
            autoGenerateBackgroundMode = legacyIconBackground.number
        )
    }
}