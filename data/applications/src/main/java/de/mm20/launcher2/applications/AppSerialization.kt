package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.core.content.getSystemService
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import org.json.JSONObject

class LauncherAppSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as LauncherApp
        val json = JSONObject()
        json.put("package", searchable.componentName.packageName)
        json.put("activity", searchable.componentName.className)
        json.put("user", searchable.userSerialNumber)
        return json.toString()
    }

    override val typePrefix: String
        get() = "app"
}

class LauncherAppDeserializer(val context: Context) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        val json = JSONObject(serialized)
        val launcherApps = context.getSystemService<LauncherApps>()!!
        val userManager = context.getSystemService<UserManager>()!!
        val userSerial = json.optLong("user")
        val user = userManager.getUserForSerialNumber(userSerial) ?: return null
        val pkg = json.getString("package")
        val intent = Intent().also {
            it.component = ComponentName(pkg, json.getString("activity"))
        }
        try {
            val launcherActivityInfo = launcherApps.resolveActivity(intent, user) ?: return null
            return LauncherApp(context, launcherActivityInfo)
        } catch (e: SecurityException) {
            return null
        }
    }

}
