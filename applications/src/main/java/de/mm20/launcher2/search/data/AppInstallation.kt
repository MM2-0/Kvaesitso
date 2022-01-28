package de.mm20.launcher2.search.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import de.mm20.launcher2.applications.R
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.Settings.IconSettings.LegacyIconBackground

class AppInstallation(
        val session: PackageInstaller.SessionInfo
) : Application(
        label = session.appLabel?.toString() ?: "",
        `package` = session.appPackageName ?: "",
        activity = "",
        flags = 0,
        version = null
) {

    override val key: String
        get() = "installer://${session.installerPackageName}:${session.appPackageName}"

    override val badgeKey: String
        get() = "app://${session.appPackageName}"

    override fun getLaunchIntent(context: Context): Intent? {
        return session.createDetailsIntent()
    }

    override fun getPlaceholderIcon(context: Context): LauncherIcon {
        return LauncherIcon(
                foreground = ContextCompat.getDrawable(context, R.drawable.ic_file_android)!!,
                background = ColorDrawable(ContextCompat.getColor(context, R.color.grey)),
                foregroundScale = 0.5f)
    }

    override suspend fun loadIcon(context: Context, size: Int, legacyIconBackground: LegacyIconBackground): LauncherIcon? {
        val icon = session.appIcon ?: return getPlaceholderIcon(context)
        val foreground = BitmapDrawable(context.resources, icon)
        foreground.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(0f)
        })
        return LauncherIcon(
                foreground = foreground,
                background = ColorDrawable(ContextCompat.getColor(context, R.color.grey))
        )
    }

    override fun getStoreDetails(context: Context): StoreLink? {
        return getStoreLinkForInstaller(session.installerPackageName, `package`)
    }

    companion object {
        fun search(context: Context): List<AppInstallation> {
            val installer = context.packageManager.packageInstaller
            val sessions = installer.allSessions
            val results = sessions.mapNotNull {
                if (it.appLabel != null && it.isActive) AppInstallation(it) else null
            }
            return results
        }
    }
}