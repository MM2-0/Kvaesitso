package de.mm20.launcher2.search.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.appshortcuts.R
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.getSerialNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppShortcut(
    context: Context,
    val launcherShortcut: ShortcutInfo,
    val appName: String
) : Searchable() {

    override val label: String
        get() = launcherShortcut.shortLabel?.toString() ?: ""


    internal val userSerialNumber: Long = launcherShortcut.userHandle.getSerialNumber(context)
    val isMainProfile = launcherShortcut.userHandle == Process.myUserHandle()

    override val key: String
        get() = if (isMainProfile) {
            "shortcut://${launcherShortcut.`package`}/${launcherShortcut.id}"
        } else {
            "shortcut://${launcherShortcut.`package`}/${launcherShortcut.id}:${userSerialNumber}"
        }

    override fun getLaunchIntent(context: Context): Intent? {
        return launcherShortcut.intent
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
                scale = 0.5f,
            ),
            backgroundLayer = ColorLayer(0xFF3DDA84.toInt()),
        )
    }

    override suspend fun loadIcon(
        context: Context,
        size: Int,
    ): LauncherIcon? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val icon = withContext(Dispatchers.IO) {
            launcherApps.getShortcutIconDrawable(
                launcherShortcut,
                context.resources.displayMetrics.densityDpi
            )
        } ?: return null
        if (icon is AdaptiveIconDrawable) {
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
}