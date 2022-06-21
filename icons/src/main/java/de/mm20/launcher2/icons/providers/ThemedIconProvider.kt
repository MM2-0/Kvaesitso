package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import androidx.core.content.res.ResourcesCompat
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.getDrawableOrNull
import de.mm20.launcher2.ktx.obtainTypedArrayOrNull
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.Searchable

internal class ThemedIconProvider(
    private val context: Context,
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


    private suspend fun getGreyscaleIcon(packageName: String): IconPackIcon? {
        val iconDao = AppDatabase.getInstance(context).iconDao()
        return iconDao.getGreyscaleIcon(ComponentName(packageName, packageName).flattenToString())
            ?.let { IconPackIcon(it) }

    }

    private fun getStaticIcon(resources: Resources, resId: Int): LauncherIcon? {
        try {
            val fg = ResourcesCompat.getDrawable(resources, resId, null) ?: return null
            return StaticLauncherIcon(
                foregroundLayer = TintedIconLayer(
                    icon = fg,
                    scale = 0.5f,
                ),
                backgroundLayer = ColorLayer()
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
            var defaultMinute = 0
            var hourIndex: Int? = null
            var defaultHour = 0
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
                    "com.android.launcher3.DEFAULT_HOUR" -> {
                        i++
                        defaultHour = array.getInt(i, 0)
                    }
                    "com.android.launcher3.DEFAULT_MINUTE" -> {
                        i++
                        defaultMinute = array.getInt(i, 0)
                    }
                }
                i++
            }
            if (drawable != null && minuteIndex != null && hourIndex != null) {

                return StaticLauncherIcon(
                    foregroundLayer = TintedClockLayer(
                        sublayers = (0 until drawable.numberOfLayers).map {
                            val drw =  drawable.getDrawable(it)
                            if (drw is RotateDrawable) {
                                drw.level = when (it) {
                                    hourIndex -> {
                                        (12 - defaultHour) * 60
                                    }
                                    minuteIndex -> {
                                        (60 - defaultMinute)
                                    }
                                    else -> 0
                                }
                            }
                            ClockSublayer(
                                drawable = drw,
                                role = when (it) {
                                    hourIndex -> ClockSublayerRole.Hour
                                    minuteIndex -> ClockSublayerRole.Minute
                                    else -> ClockSublayerRole.Static
                                }
                            )
                        },
                        scale = 1.5f,
                    ),
                    backgroundLayer = ColorLayer()
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

            return DynamicCalendarIcon(
                resources = resources,
                resourceIds = IntArray(31) {
                    array.getResourceId(it, 0).takeIf { it != 0 } ?: return null
                },
                isThemed = true
            )
        } catch (e: Resources.NotFoundException) {
        }
        return null
    }
}