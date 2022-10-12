package de.mm20.launcher2.appshortcuts

import android.content.Context
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.search.PinnableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.data.LauncherShortcut
import de.mm20.launcher2.search.data.LegacyShortcut
import de.mm20.launcher2.search.Searchable
import org.json.JSONObject
import org.koin.core.component.KoinComponent


class LauncherShortcutSerializer : SearchableSerializer {
    override fun serialize(searchable: PinnableSearchable): String {
        searchable as LauncherShortcut
        return jsonObjectOf(
            "packagename" to searchable.launcherShortcut.`package`,
            "id" to searchable.launcherShortcut.id,
            "user" to searchable.userSerialNumber,
        ).toString()
    }

    override val typePrefix: String
        get() = "shortcut"

}

class LauncherShortcutDeserializer(
    val context: Context
) : SearchableDeserializer, KoinComponent {

    override fun deserialize(serialized: String): PinnableSearchable? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        if (!launcherApps.hasShortcutHostPermission()) return null
        else {
            val json = JSONObject(serialized)
            val packageName = json.getString("packagename")
            val id = json.getString("id")
            val userSerial = json.optLong("user")
            val query = LauncherApps.ShortcutQuery()
            query.setPackage(packageName)
            query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER
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
                return LauncherShortcut(
                    context = context,
                    launcherShortcut = shortcuts[0],
                )
            }
        }
    }
}

class LegacyShortcutSerializer: SearchableSerializer {
    override fun serialize(searchable: PinnableSearchable): String {
        searchable as LegacyShortcut
        return jsonObjectOf(
            "label" to searchable.label,
            "intent" to searchable.intent.toUri(0),
            "iconResource" to searchable.iconResource?.let {
                jsonObjectOf(
                    "package" to it.packageName,
                    "resource" to it.resourceName,
                )
            }
        ).toString()
    }

    override val typePrefix: String
        get() = "legacyshortcut"
}

class LegacyShortcutDeserializer(
    val context: Context
): SearchableDeserializer {
    override fun deserialize(serialized: String): PinnableSearchable {
        val json = JSONObject(serialized)
        val label = json.getString("label")
        val intent = Intent.parseUri(json.getString("intent"), 0)
        val iconResourceObj = json.optJSONObject("iconResource")
        val iconResource = iconResourceObj?.let {
            ShortcutIconResource().apply {
                packageName = iconResourceObj.getString("package")
                resourceName = iconResourceObj.getString("resource")
            }
        }

        val packageName = intent.`package` ?: intent.component?.packageName

        val appName = try {
            packageName?.let {
                context
                    .packageManager
                    .getApplicationInfo(it, 0)
                    .loadLabel(context.packageManager)
                    .toString()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        return LegacyShortcut(
            intent = intent,
            label = label,
            iconResource = iconResource,
            appName = appName,
        )
    }

}