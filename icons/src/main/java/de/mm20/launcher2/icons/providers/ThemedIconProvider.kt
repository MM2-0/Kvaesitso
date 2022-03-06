package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.obtainTypedArrayOrNull
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.Searchable

internal class ThemedIconProvider(
    private val context: Context,
    private val colors: ThemeColors,
) : IconProvider {

    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon? {
        if (searchable !is Application) return null
        val icon = getGreyscaleIcon(searchable.`package`) ?: return null
        val resId = icon.drawable?.toIntOrNull() ?: return null
        try {
            val resources = context.packageManager.getResourcesForApplication(icon.iconPack)
            return getClockIcon(resources, resId) ?: getCalendarIcon(
                resources,
                resId,
                iconProviderPackage = icon.iconPack
            ) ?: getStaticIcon(resources, resId)
        } catch (e: PackageManager.NameNotFoundException) {
            CrashReporter.logException(e)
        }
        return null
    }


    private suspend fun getGreyscaleIcon(packageName: String): Icon? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        return iconDao.getGreyscaleIcon(ComponentName(packageName, packageName).flattenToString())
            ?.let { Icon(it) }

    }

    private fun getStaticIcon(resources: Resources, resId: Int): LauncherIcon? {
        try {
            val fg = ResourcesCompat.getDrawable(resources, resId, null) ?: return null
            fg.setTint(colors.foreground)
            return LauncherIcon(
                foreground = fg,
                foregroundScale = 0.5f,
                background = ColorDrawable(colors.background)
            )
        } catch (e: Resources.NotFoundException) {
            return null
        }
    }

    private fun getClockIcon(resources: Resources, resId: Int): LauncherIcon? {
        try {
            val array = resources.obtainTypedArrayOrNull(resId) ?: return null
            var i = 0
            var drawable: LayerDrawable? = null
            var minuteIndex: Int? = null
            var hourIndex: Int? = null
            while (i < array.length()) {
                when (array.getString(i)) {
                    "com.android.launcher3.LEVEL_PER_TICK_ICON_ROUND" -> {
                        i++
                        drawable = array.getDrawable(i) as? LayerDrawable
                    }
                    "com.android.launcher3.HOUR_LAYER_INDEX" -> {
                        i++
                        hourIndex = array.getInt(i, -1).takeIf { it != -1 }
                    }
                    "com.android.launcher3.MINUTE_LAYER_INDEX" -> {
                        i++
                        minuteIndex = array.getInt(i, -1).takeIf { it != -1 }
                    }
                }
                i++
            }
            if (drawable != null && minuteIndex != null && hourIndex != null) {
                drawable.setTint(colors.foreground)
                return ClockDynamicLauncherIcon(
                    foreground = drawable,
                    background = ColorDrawable(colors.background),
                    foregroundScale = 1.5f,
                    backgroundScale = 1f,
                    hourLayer = hourIndex,
                    minuteLayer = minuteIndex,
                    secondLayer = -1,
                )
            }
        } catch (e: Resources.NotFoundException) {
        }
        return null
    }

    private fun getCalendarIcon(
        resources: Resources,
        resId: Int,
        iconProviderPackage: String
    ): LauncherIcon? {
        try {
            val array = resources.obtainTypedArrayOrNull(resId) ?: return null
            if (array.length() != 31) return null

            return ThemedCalendarDynamicLauncherIcon(
                background = ColorDrawable(colors.background),
                packageName = iconProviderPackage,
                foregroundIds = IntArray(31) {
                    array.getResourceId(it, 0).takeIf { it != 0 } ?: return null
                },
                foregroundTint = colors.foreground,
                foregroundScale = 0.5f,
            )

        } catch (e: Resources.NotFoundException) {
        }
        return null
    }
}