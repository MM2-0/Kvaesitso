package de.mm20.launcher2.search.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.core.content.getSystemService
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.IconSettings.LegacyIconBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

/**
 * An [Application] based on an [android.content.pm.LauncherActivityInfo]
 */
class LauncherApp(
    context: Context,
    public val launcherActivityInfo: LauncherActivityInfo
) : Application(
    label = launcherActivityInfo.label.toString(),
    `package` = launcherActivityInfo.applicationInfo.packageName,
    activity = launcherActivityInfo.name,
    flags = launcherActivityInfo.applicationInfo.flags,
    version = getPackageVersionName(context, launcherActivityInfo.applicationInfo.packageName),
    shortcuts = run {
        val appShortcuts = mutableListOf<AppShortcut>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcherApps = context.getSystemService<LauncherApps>()!!
            if (!launcherApps.hasShortcutHostPermission()) return@run appShortcuts
            val query = LauncherApps.ShortcutQuery()
                .setPackage(launcherActivityInfo.applicationInfo.packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            val shortcuts = try {
                launcherApps.getShortcuts(query, launcherActivityInfo.user)
            } catch (e: IllegalStateException) {
                emptyList<ShortcutInfo>()
            }
            appShortcuts.addAll(shortcuts?.map {
                AppShortcut(
                    context,
                    it,
                    launcherActivityInfo.label.toString()
                )
            }
                ?: emptyList())
        }
        appShortcuts
    }
), KoinComponent {

    internal val userSerialNumber: Long = launcherActivityInfo.user.getSerialNumber(context)
    private val isMainProfile = launcherActivityInfo.user == Process.myUserHandle()

    override val badgeKey: String =
        if (isMainProfile) "app://${`package`}" else "profile://$userSerialNumber"

    override val key: String
        get() = if (isMainProfile) "app://$`package`:$activity" else "app://$`package`:$activity:${userSerialNumber}"

    fun getUser(): UserHandle? {
        return launcherActivityInfo.user
    }

    override suspend fun loadIcon(context: Context, size: Int, legacyIconBackground: LegacyIconBackground): LauncherIcon? {
        try {
            val icon =
                withContext(Dispatchers.IO) {
                    launcherActivityInfo.getIcon(context.resources.displayMetrics.densityDpi)

                } ?: return null
            if (icon is AdaptiveIconDrawable) {
                return LauncherIcon(
                    foreground = icon.foreground ?: return null,
                    background = icon.background,
                    foregroundScale = 1.5f,
                    backgroundScale = 1.5f
                )
            } else {
                return LauncherIcon(
                    foreground = icon,
                    foregroundScale = 0.7f,
                    autoGenerateBackgroundMode = legacyIconBackground.number
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        if (isMainProfile) {
            val intent = Intent()
            intent.component = ComponentName(`package`, activity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent, options)
        } else {
            try {
                launcherApps.startMainActivity(
                    ComponentName(`package`, activity),
                    launcherActivityInfo.user,
                    null,
                    options
                )
            } catch (e: SecurityException) {
                return false
            }
        }
        return true
    }

    companion object {

        fun getPackageVersionName(context: Context, packageName: String): String? {
            return try {
                context.packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}