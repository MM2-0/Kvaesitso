package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import android.util.Log
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.StringNormalizer
import org.json.JSONObject

internal class LockedPrivateProfileAppSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String {
        searchable as LockedPrivateProfileApp
        val json = JSONObject()
        json.put("package", searchable.componentName.packageName)
        json.put("activity", searchable.componentName.className)
        json.put("user", searchable.userSerialNumber)
        return json.toString()
    }

    override val typePrefix: String
        get() = "app"
}

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
        try {
            val json = JSONObject(serialized)
            val launcherApps = context.getSystemService<LauncherApps>()!!
            val userManager = context.getSystemService<UserManager>()!!
            val userSerial = json.optLong("user", -1L)
            val user = if (userSerial == -1L) Process.myUserHandle() else (userManager.getUserForSerialNumber(userSerial) ?: return null)

            val pkg = json.getString("package")
            val activity = json.getString("activity")

            val componentName = ComponentName(pkg, activity)

            if (isAtLeastApiLevel(35)) {
                val launcherUser = launcherApps.getLauncherUserInfo(user) ?: return null
                if (launcherUser.userType == UserManager.USER_TYPE_PROFILE_PRIVATE && userManager.isQuietModeEnabled(
                        user
                    )
                ) {
                    return LockedPrivateProfileApp(
                        label = context.getString(R.string.app_label_locked_profile),
                        componentName = componentName,
                        user = user,
                        userSerialNumber = userSerial
                    )
                }
            }

            val intent = Intent().also {
                it.component = componentName
            }
            val launcherActivityInfo = launcherApps.resolveActivity(intent, user) ?: return null
            return LauncherApp(context, launcherActivityInfo)
        } catch (e: SecurityException) {
            Log.e("MM20", "Failed to deserialize app: $serialized", e)
            return null
        }
    }

}
