package de.mm20.launcher2.search.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import de.mm20.launcher2.applications.R
import de.mm20.launcher2.compat.PackageManagerCompat
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.SavableSearchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LauncherApp(
    val launcherActivityInfo: LauncherActivityInfo,
    override val label: String,
    val `package`: String,
    val activity: String,
    val flags: Int,
    val version: String?,
    internal val userSerialNumber: Long,
    override val labelOverride: String? = null,
) : SavableSearchable {

    constructor(context: Context, launcherActivityInfo: LauncherActivityInfo): this(
        launcherActivityInfo,
        label = launcherActivityInfo.label.toString(),
        `package` = launcherActivityInfo.applicationInfo.packageName,
        activity = launcherActivityInfo.name,
        flags = launcherActivityInfo.applicationInfo.flags,
        version = getPackageVersionName(context, launcherActivityInfo.applicationInfo.packageName),
        userSerialNumber = launcherActivityInfo.user.getSerialNumber(context)
    )

    val isMainProfile = launcherActivityInfo.user == Process.myUserHandle()

    val canUninstall: Boolean
        get() = flags and ApplicationInfo.FLAG_SYSTEM == 0 && isMainProfile

    override val domain: String = Domain
    override val preferDetailsOverLaunch: Boolean = false

    override fun overrideLabel(label: String): LauncherApp {
        return this.copy(labelOverride = label)
    }

    override val key: String
        get() = if (isMainProfile) "${domain}://$`package`:$activity" else "${domain}://$`package`:$activity:${userSerialNumber}"

    fun getUser(): UserHandle? {
        return launcherActivityInfo.user
    }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                icon = ContextCompat.getDrawable(context, R.drawable.ic_file_android)!!,
                scale = 0.5f,
                color = 0xff3dda84.toInt(),
            ),
            backgroundLayer = ColorLayer(0xff3dda84.toInt())
        )
    }

    override suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean,
    ): LauncherIcon? {
        try {
            val icon =
                withContext(Dispatchers.IO) {
                    launcherActivityInfo.getIcon(context.resources.displayMetrics.densityDpi)

                } ?: return null
            if (icon is AdaptiveIconDrawable) {
                if (themed && isAtLeastApiLevel(33) && icon.monochrome != null) {
                    return StaticLauncherIcon(
                        foregroundLayer = TintedIconLayer(
                            scale = 1f,
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

    fun getStoreDetails(context: Context): StoreLink? {
        val pm = context.packageManager
        return try {
            val installSourceInfo = PackageManagerCompat.getInstallSource(pm, `package`)
            getStoreLinkForInstaller(installSourceInfo.initiatingPackageName, `package`)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun uninstall(context: Context) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:$`package`")
        context.startActivity(intent)
    }

    fun openAppInfo(context: Context) {
        val launcherApps = context.getSystemService<LauncherApps>()!!

        launcherApps.startAppDetailsActivity(
            ComponentName(`package`, activity),
            getUser(),
            null,
            null
        )
    }

    suspend fun shareApkFile(context: Context) {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        val fileCopy = java.io.File(
            context.cacheDir,
            "${`package`}-${version}.apk"
        )
        withContext(Dispatchers.IO) {
            try {
                val user = getUser()
                val info = if (user != null) {
                    launcherApps.getApplicationInfo(`package`, 0, user)
                } else {
                    context.packageManager.getApplicationInfo(`package`, 0)
                }
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

        const val Domain = "app"
    }
}

data class StoreLink(
    val label: String,
    val url: String
)