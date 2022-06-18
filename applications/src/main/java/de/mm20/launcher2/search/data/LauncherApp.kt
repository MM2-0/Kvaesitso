package de.mm20.launcher2.search.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.core.content.getSystemService
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.getSerialNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

/**
 * An [Application] based on an [android.content.pm.LauncherActivityInfo]
 */
class LauncherApp(
    context: Context,
    val launcherActivityInfo: LauncherActivityInfo
) : Application(
    label = launcherActivityInfo.label.toString(),
    `package` = launcherActivityInfo.applicationInfo.packageName,
    activity = launcherActivityInfo.name,
    flags = launcherActivityInfo.applicationInfo.flags,
    version = getPackageVersionName(context, launcherActivityInfo.applicationInfo.packageName),
), KoinComponent {

    internal val userSerialNumber: Long = launcherActivityInfo.user.getSerialNumber(context)
    val isMainProfile = launcherActivityInfo.user == Process.myUserHandle()

    override val key: String
        get() = if (isMainProfile) "app://$`package`:$activity" else "app://$`package`:$activity:${userSerialNumber}"

    fun getUser(): UserHandle? {
        return launcherActivityInfo.user
    }

    override suspend fun loadIcon(
        context: Context,
        size: Int,
    ): LauncherIcon? {
        try {
            val icon =
                withContext(Dispatchers.IO) {
                    launcherActivityInfo.getIcon(context.resources.displayMetrics.densityDpi)

                } ?: return null
            if (icon is AdaptiveIconDrawable) {
                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = icon.foreground,
                        scale = 1.5f,
                    ),
                    backgroundLayer = StaticIconLayer(
                        icon = icon.background,
                        scale = 1.5f,
                    )
                )
            } else {
                return StaticLauncherIcon(
                    foregroundLayer = StaticIconLayer(
                        icon = icon,
                        scale = 1f,
                    ),
                    backgroundLayer = TransparentLayer
                )
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
    }

    override fun launch(context: Context, options: Bundle?): Boolean {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        try {
            launcherApps.startMainActivity(
                ComponentName(`package`, activity),
                launcherActivityInfo.user,
                null,
                options
            )
        } catch (e: SecurityException) {
            return false
        } catch (e: ActivityNotFoundException) {
            return false
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