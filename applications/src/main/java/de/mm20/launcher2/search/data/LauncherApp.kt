package de.mm20.launcher2.search.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.*
import androidx.core.content.getSystemService
import de.mm20.launcher2.icons.IconPackManager
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.ktx.getSerialNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * An [Application] based on an [android.content.pm.LauncherActivityInfo]
 */
class LauncherApp(
        context: Context,
        private val launcherActivityInfo: LauncherActivityInfo
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
                appShortcuts.addAll(shortcuts?.map { AppShortcut(context, it, launcherActivityInfo.label.toString()) }
                        ?: emptyList())
            }
            appShortcuts
        }
) {

    private val userSerialNumber: Long = launcherActivityInfo.user.getSerialNumber(context)
    private val isMainProfile = launcherActivityInfo.user == Process.myUserHandle()

    override val badgeKey: String = if (isMainProfile) "app://${`package`}" else "profile://$userSerialNumber"

    override val key: String
        get() = if (isMainProfile) "app://$`package`:$activity" else "app://$`package`:$activity:${userSerialNumber}"

    override fun serialize(): String {
        val json = JSONObject()
        json.put("package", `package`)
        json.put("activity", activity)
        json.put("user", userSerialNumber)
        return json.toString()
    }

    fun getUser(): UserHandle? {
        return launcherActivityInfo.user
    }

    override suspend fun loadIconAsync(context: Context, size: Int): LauncherIcon? {
        return withContext(Dispatchers.IO) {
            IconPackManager.getInstance(context).getIcon(context, launcherActivityInfo, size)
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

        fun deserialize(context: Context, serialized: String): LauncherApp? {
            val json = JSONObject(serialized)
            val launcherApps = context.getSystemService<LauncherApps>()!!
            val userManager = context.getSystemService<UserManager>()!!
            val userSerial = json.optLong("user")
            val user = userManager.getUserForSerialNumber(userSerial) ?: Process.myUserHandle()
            val pkg = json.getString("package")
            val intent = Intent().also {
                it.component = ComponentName(pkg, json.getString("activity"))
            }
            val launcherActivityInfo = launcherApps.resolveActivity(intent, user) ?: return null
            return LauncherApp(context, launcherActivityInfo)
        }

        fun getPackageVersionName(context: Context, packageName: String): String? {
            return try {
                context.packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}