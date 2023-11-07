package de.mm20.launcher2.appshortcuts

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.search.AppProfile
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableSerializer

/**
 * Shortcut class that is used when a [LauncherShortcut] is not available, e.g. missing permissions
 * when Kvaesitso is not set as default launcher.
 */
internal class UnavailableShortcut(
    override val label: String,
    override val appName: String?,
    override val packageName: String,
    val shortcutId: String,
    val isMainProfile: Boolean,
    val userSerial: Long,
): AppShortcut {


    override val key: String
        get() = if (isMainProfile) {
            "$domain://${packageName}/${shortcutId}"
        } else {
            "$domain://${packageName}/${shortcutId}:userSerial"
        }

    override val labelOverride: String?
        get() = null
    override val componentName: ComponentName?
        get() = null

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = context.getDrawable(R.drawable.ic_file_android)!!,
                color = 0xFF333333.toInt()
            ),
            backgroundLayer = ColorLayer(0xFF333333.toInt()),
        )
    }

    override val domain: String
        get() = LauncherShortcut.Domain

    override fun overrideLabel(label: String): SavableSearchable {
        return this
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        return false
    }

    override fun getSerializer(): SearchableSerializer {
        return UnavailableShortcutSerializer()
    }

    override val isUnavailable: Boolean = true
    override val profile: AppProfile
        get() = if (isMainProfile) AppProfile.Personal else AppProfile.Work

    companion object {
        internal operator fun invoke(context: Context, id: String, packageName: String, userSerial: Long): UnavailableShortcut? {
            val appInfo = try {
                context.packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            return UnavailableShortcut(
                label = context.getString(R.string.shortcut_label_unavailable),
                appName = appInfo.loadLabel(context.packageManager).toString(),
                packageName = packageName,
                shortcutId = id,
                isMainProfile = userManager.getUserForSerialNumber(userSerial) == Process.myUserHandle(),
                userSerial = userSerial,
            )
        }
    }
}