package de.mm20.launcher2.search.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Bundle
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.appshortcuts.R
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Represents a modern (Android O+) launcher shortcut
 */
data class LauncherShortcut(
    val launcherShortcut: ShortcutInfo,
    override val appName: String?,
    internal val userSerialNumber: Long,
    override val labelOverride: String? = null,
) : AppShortcut {

    override val domain: String = Domain

    constructor(
        context: Context,
        launcherShortcut: ShortcutInfo,
    ): this(
        launcherShortcut = launcherShortcut,
        appName = try {
            context.packageManager.getApplicationInfo(launcherShortcut.`package`, 0)
                .loadLabel(context.packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        },
        userSerialNumber = launcherShortcut.userHandle.getSerialNumber(context)
    )

    override val label: String
        get() = launcherShortcut.shortLabel?.toString() ?: ""

    override fun overrideLabel(label: String): LauncherShortcut {
        return this.copy(labelOverride = label)
    }

    override val preferDetailsOverLaunch: Boolean = false


    val isMainProfile = launcherShortcut.userHandle == Process.myUserHandle()

    override val key: String
        get() = if (isMainProfile) {
            "$domain://${launcherShortcut.`package`}/${launcherShortcut.id}"
        } else {
            "$domain://${launcherShortcut.`package`}/${launcherShortcut.id}:${userSerialNumber}"
        }

    override fun launch(context: Context, options: Bundle?): Boolean {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        try {
            launcherApps.startShortcut(launcherShortcut, null, options)
        } catch (e: IllegalStateException) {
            return false
        } catch (e: ActivityNotFoundException) {
            return false
        }
        return true
    }

    override fun getPlaceholderIcon(context: Context): StaticLauncherIcon {
        return StaticLauncherIcon(
            foregroundLayer = TintedIconLayer(
                color = 0xFF3DDA84.toInt(),
                icon = ContextCompat.getDrawable(context, R.drawable.ic_file_android)!!,
                scale = 0.65f,
            ),
            backgroundLayer = ColorLayer(0xFF3DDA84.toInt()),
        )
    }

    override suspend fun loadIcon(
        context: Context,
        size: Int,
        themed: Boolean,
    ): LauncherIcon? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val icon = withContext(Dispatchers.IO) {
            launcherApps.getShortcutIconDrawable(
                launcherShortcut,
                context.resources.displayMetrics.densityDpi
            )
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
        }
        return StaticLauncherIcon(
            foregroundLayer = StaticIconLayer(
                icon = icon,
                scale = 1f
            ),
            backgroundLayer = TransparentLayer
        )
    }

    companion object {
        fun fromPinRequestIntent(context: Context, data: Intent): LauncherShortcut? {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val pinRequest = launcherApps.getPinItemRequest(data)
            val shortcutInfo = pinRequest?.shortcutInfo ?: return null
            if (!pinRequest.accept()) return null
            return LauncherShortcut(
                context,
                shortcutInfo,
            )
        }

        const val Domain = "shortcut"
    }

}