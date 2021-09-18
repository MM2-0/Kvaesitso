package de.mm20.launcher2.search.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.core.content.ContextCompat
import de.mm20.launcher2.applications.R
import de.mm20.launcher2.compat.PackageManagerCompat
import de.mm20.launcher2.icons.LauncherIcon
import org.json.JSONObject

abstract class Application(
    override val label: String,
    val `package`: String,
    val activity: String,
    val flags: Int,
    val version: String?,
    val shortcuts: List<AppShortcut> = emptyList()
) : Searchable() {

    override val badgeKey: String
        get() = "app://${`package`}"

    override fun serialize(): String {
        val json = JSONObject()
        json.put("package", `package`)
        json.put("activity", activity)
        return json.toString()
    }

    override fun getLaunchIntent(context: Context): Intent? {
        val intent = Intent()
        intent.component = ComponentName(`package`, activity)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        return LauncherIcon(
            foreground = ContextCompat.getDrawable(context, R.drawable.ic_app_placeholder)!!,
            background = ColorDrawable(ContextCompat.getColor(context, R.color.lightgreen)),
            foregroundScale = 0.5f
        )
    }

    open fun getStoreDetails(context: Context): StoreLink? {
        val pm = context.packageManager
        val installSourceInfo = PackageManagerCompat.getInstallSource(pm, `package`)
        return getStoreLinkForInstaller(installSourceInfo.initiatingPackageName, `package`)
    }

    override val key: String
        get() = "app://$`package`:$activity"

    companion object {
        internal fun getStoreLinkForInstaller(installerPackage: String?, packageName: String?): StoreLink? {
            if (packageName == null) return null
            return when (installerPackage) {
                "de.amazon.mShop.android", "com.amazon.venezia" -> {
                    StoreLink(
                        "Amazon App Shop",
                        "http://www.amazon.com/gp/mas/dl/android?p=${packageName}"
                    )
                }
                "com.android.vending" -> {
                    StoreLink(
                        "Google Play Store",
                        "https://play.google.com/store/apps/details?id=${packageName}"
                    )
                }
                "org.fdroid.fdroid", "com.aurora.adroid" -> {
                    StoreLink(
                        "F-Droid",
                        "https://f-droid.org/packages/${packageName}"
                    )
                }
                else -> null
            }
        }
    }
}

data class StoreLink(
    val label: String,
    val url: String
)