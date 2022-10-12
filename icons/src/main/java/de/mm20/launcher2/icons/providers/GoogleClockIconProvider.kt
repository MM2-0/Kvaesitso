package de.mm20.launcher2.icons.providers

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import androidx.core.content.res.ResourcesCompat
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.search.PinnableSearchable
import de.mm20.launcher2.search.Searchable
import de.mm20.launcher2.search.data.LauncherApp

class GoogleClockIconProvider(val context: Context) : IconProvider {
    override suspend fun getIcon(searchable: PinnableSearchable, size: Int): LauncherIcon? {
        if (searchable !is LauncherApp) return null
        if (searchable.`package` != "com.google.android.deskclock") return null
        val pm = context.packageManager
        val appInfo = try {
            pm.getApplicationInfo(
                "com.google.android.deskclock",
                PackageManager.GET_META_DATA
            )
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        val drawable =
            appInfo.metaData.getInt("com.android.launcher3.LEVEL_PER_TICK_ICON_ROUND")
        val resources = pm.getResourcesForApplication(appInfo)
        val baseIcon = try {
            ResourcesCompat.getDrawable(resources, drawable, null) as? AdaptiveIconDrawable
                ?: return null
        } catch (e: Resources.NotFoundException) {
            return null
        }
        val foreground = baseIcon.foreground as? LayerDrawable ?: return null
        val hourLayer =
            appInfo.metaData.getInt("com.android.launcher3.HOUR_LAYER_INDEX")
        val minuteLayer =
            appInfo.metaData.getInt("com.android.launcher3.MINUTE_LAYER_INDEX")
        val secondLayer =
            appInfo.metaData.getInt("com.android.launcher3.SECOND_LAYER_INDEX")

        val defaultHour =
            appInfo.metaData.getInt("com.android.launcher3.DEFAULT_HOUR")
        val defaultMinute =
            appInfo.metaData.getInt("com.android.launcher3.DEFAULT_MINUTE")
        val defaultSecond =
            appInfo.metaData.getInt("com.android.launcher3.DEFAULT_SECOND")

        return StaticLauncherIcon(
            foregroundLayer = ClockLayer(
                sublayers = (0 until foreground.numberOfLayers).map {
                    val drw = foreground.getDrawable(it)
                    if (drw is RotateDrawable) {
                        drw.level = when (it) {
                            hourLayer -> {
                                (12 - defaultHour) * 60
                            }
                            minuteLayer -> {
                                (60 - defaultMinute)
                            }
                            secondLayer -> {
                                (60 - defaultSecond) * 10
                            }
                            else -> 0
                        }
                    }
                    ClockSublayer(
                        drawable = drw,
                        role = when (it) {
                            hourLayer -> ClockSublayerRole.Hour
                            minuteLayer -> ClockSublayerRole.Minute
                            secondLayer -> ClockSublayerRole.Second
                            else -> ClockSublayerRole.Static
                        }
                    )
                },
                scale = 1.5f,
            ),
            backgroundLayer = StaticIconLayer(
                icon = baseIcon.background,
                scale = 1.5f,
            )
        )
    }
}