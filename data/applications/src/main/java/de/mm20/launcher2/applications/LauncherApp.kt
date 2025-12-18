package de.mm20.launcher2.applications

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import de.mm20.launcher2.compat.PackageManagerCompat
import de.mm20.launcher2.icons.ColorLayer
import de.mm20.launcher2.icons.LauncherIcon
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.icons.TintedIconLayer
import de.mm20.launcher2.icons.TransparentLayer
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.ResultScore
import de.mm20.launcher2.search.SearchableSerializer
import de.mm20.launcher2.search.StoreLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LauncherApp(
    private val launcherActivityInfo: LauncherActivityInfo,
    override val versionName: String?,
    override val isSuspended: Boolean = false,
    internal val userSerialNumber: Long,
    override val labelOverride: String? = null,
    override val score: ResultScore = ResultScore.Unspecified,
) : Application {

    override val componentName: ComponentName
        get() = launcherActivityInfo.componentName

    override val label: String = launcherActivityInfo.label.toString()


    constructor(
        context: Context,
        launcherActivityInfo: LauncherActivityInfo,
        score: ResultScore = ResultScore.Unspecified,
    ) : this(
        launcherActivityInfo,
        versionName = getPackageVersionName(
            context,
            launcherActivityInfo.applicationInfo.packageName
        ),
        isSuspended = launcherActivityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
        userSerialNumber = launcherActivityInfo.user.getSerialNumber(context),
        score = score,
    )

    override val user: UserHandle
        get() = launcherActivityInfo.user

    private val isMainProfile = launcherActivityInfo.user == Process.myUserHandle()

    private val isSystemApp: Boolean =
        launcherActivityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0

    override val canUninstall: Boolean
        get() = !isSystemApp && isMainProfile

    override val domain: String = Domain
    override val preferDetailsOverLaunch: Boolean = false

    override fun overrideLabel(label: String): LauncherApp {
        return this.copy(labelOverride = label)
    }

    override val key: String
        // For backwards compatibility, user serial number is not included in main profile
        get() = if (isMainProfile) "${domain}://${componentName.packageName}:${componentName.className}"
        else "${domain}://${componentName.packageName}:${componentName.className}:${userSerialNumber}"


    override suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean,
    ): LauncherIcon? {
        try {
            val icon =
                withContext(Dispatchers.IO) {
                    val density = size / (108 / 1.5)
                    launcherActivityInfo.getIcon(0)

                } ?: return null
            if (icon is AdaptiveIconDrawable) {
                if (themed && isAtLeastApiLevel(33) && icon.monochrome != null) {
                    return StaticLauncherIcon(
                        foregroundLayer = TintedIconLayer(
                            scale = 1.5f,
                            icon = icon.monochrome!!,
                        ),
                        backgroundLayer = ColorLayer()
                    )
                }
                return StaticLauncherIcon(
                    foregroundLayer = icon.foreground?.let {
                        StaticIconLayer(
                            icon = it,
                            scale = 1.5f,
                        )
                    } ?: TransparentLayer,
                    backgroundLayer = icon.background?.let {
                        StaticIconLayer(
                            icon = it,
                            scale = 1.5f,
                        )
                    } ?: TransparentLayer,
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
        if (isAtLeastApiLevel(31)) {
            options?.putInt("android.activity.splashScreenStyle", 1)
        }
        try {
            launcherApps.startMainActivity(
                componentName,
                launcherActivityInfo.user,
                null,
                options
            )
        } catch (e: SecurityException) {
            Log.e("MM20", "Could not launch app", e)
            return false
        } catch (e: ActivityNotFoundException) {
            Log.e("MM20", "Could not launch app", e)
            return false
        }
        return true
    }

    override fun getStoreDetails(context: Context): StoreLink? {
        val pm = context.packageManager
        return try {
            val installSourceInfo =
                PackageManagerCompat.getInstallSource(pm, componentName.packageName)
            getStoreLinkForInstaller(
                installSourceInfo.initiatingPackageName,
                componentName.packageName
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun uninstall(context: Context) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:${componentName.packageName}")
        context.startActivity(intent)
    }

    override fun openAppDetails(context: Context) {
        val launcherApps = context.getSystemService<LauncherApps>()!!

        launcherApps.startAppDetailsActivity(
            componentName,
            user,
            null,
            null
        )
    }

    override val canShareApk: Boolean = true
    override suspend fun shareApkFile(context: Context) {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        val fileCopy = java.io.File(
            context.cacheDir,
            "${componentName.packageName}-${versionName}.apk"
        )
        withContext(Dispatchers.IO) {
            try {
                val info = launcherApps.getApplicationInfo(componentName.packageName, 0, user)
                val file = java.io.File(info.publicSourceDir)

                try {
                    file.copyTo(fileCopy, false)
                } catch (e: FileAlreadyExistsException) {
                    // Do nothing. If the file is already there we don't have to copy it again.
                }
            } catch (e: PackageManager.NameNotFoundException) {
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            fileCopy
        )
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "application/vnd.android.package-archive"
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(shareIntent, null))
        }
    }

    override fun getActivityInfo(context: Context): ActivityInfo? {
        if (isAtLeastApiLevel(31)) {
            return launcherActivityInfo.activityInfo
        }
        return super.getActivityInfo(context)
    }

    companion object {
        private fun getStoreLinkForInstaller(
            installerPackage: String?,
            packageName: String?
        ): StoreLink? {
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

        fun getPackageVersionName(context: Context, packageName: String): String? {
            return try {
                context.packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        fun isSuspended(context: Context, packageName: String): Boolean {
            return try {
                context.packageManager.getApplicationInfo(
                    packageName,
                    0
                ).flags and ApplicationInfo.FLAG_SUSPENDED != 0
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        const val Domain = "app"
    }

    override fun getSerializer(): SearchableSerializer {
        return LauncherAppSerializer()
    }
}