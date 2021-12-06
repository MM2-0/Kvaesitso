package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.applications.R
import de.mm20.launcher2.badges.Badge
import de.mm20.launcher2.badges.BadgeProvider
import de.mm20.launcher2.graphics.BadgeDrawable
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.preferences.LauncherPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.IllegalStateException

@RequiresApi(Build.VERSION_CODES.N_MR1)
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
            return if (LauncherPreferences.instance.shortcutBadges) {
                if (isMainProfile) "shortcut://${launcherShortcut.activity?.flattenToShortString()}" else "profile://$userSerialNumber"
            } else {
                "null"
            }
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
                foregroundScale = 0.5f)
    }

    override suspend fun loadIcon(context: Context, size: Int): LauncherIcon? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val icon = withContext(Dispatchers.IO) {
            launcherApps.getShortcutIconDrawable(launcherShortcut, context.resources.displayMetrics.densityDpi)
        } ?: return null
        if (isAtLeastApiLevel(Build.VERSION_CODES.O) && icon is AdaptiveIconDrawable) {
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
                autoGenerateBackgroundMode = LauncherPreferences.instance.legacyIconBg.toInt()
        )
    }
}