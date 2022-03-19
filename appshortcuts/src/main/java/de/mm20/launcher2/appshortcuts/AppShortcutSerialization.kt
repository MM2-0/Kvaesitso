package de.mm20.launcher2.appshortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.AppShortcut
import de.mm20.launcher2.search.data.Searchable
import org.json.JSONObject
import org.koin.core.component.KoinComponent


class AppShortcutSerializer : SearchableSerializer {
    override fun serialize(searchable: Searchable): String {
        searchable as AppShortcut
        return jsonObjectOf(
            "packagename" to searchable.launcherShortcut.`package`,
            "id" to searchable.launcherShortcut.id,
            "user" to searchable.userSerialNumber,
        ).toString()
    }

    override val typePrefix: String
        get() = "shortcut"

}

class AppShortcutDeserializer(
    val context: Context
) : SearchableDeserializer, KoinComponent {

    override fun deserialize(serialized: String): Searchable? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        if (!launcherApps.hasShortcutHostPermission()) return null
        else {
            val json = JSONObject(serialized)
            val packageName = json.getString("packagename")
            val id = json.getString("id")
            val userSerial = json.optLong("user")
            val query = LauncherApps.ShortcutQuery()
            query.setPackage(packageName)
            query.setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            query.setShortcutIds(mutableListOf(id))
            val userManager = context.getSystemService<UserManager>()!!
            val user = userManager.getUserForSerialNumber(userSerial) ?: Process.myUserHandle()
            val shortcuts = try {
                launcherApps.getShortcuts(query, user)
            } catch (e: IllegalStateException) {
                return null
            }
            val pm = context.packageManager
            val appName = try {
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }
            if (shortcuts == null || shortcuts.isEmpty()) {
                return null
            } else {
                val activity = shortcuts[0].activity
                return AppShortcut(
                    context = context,
                    launcherShortcut = shortcuts[0],
                    appName = appName
                )
            }
        }
    }
}