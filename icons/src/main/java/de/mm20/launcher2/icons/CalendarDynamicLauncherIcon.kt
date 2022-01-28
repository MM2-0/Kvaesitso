package de.mm20.launcher2.icons

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import de.mm20.launcher2.ktx.getDrawableOrNull
import java.util.*
import java.util.concurrent.Executors

class CalendarDynamicLauncherIcon(
    foreground: Drawable,
    background: Drawable?,
    foregroundScale: Float,
    backgroundScale: Float,
    val packageName: String,
    val drawableIds: IntArray,
) : DynamicLauncherIcon(
    foreground,
    background,
    foregroundScale,
    backgroundScale,
) {

    var currentDay = 0
    override fun update(context: Context) {
        val calendar = Calendar.getInstance()
        val day = calendar[Calendar.DAY_OF_MONTH]
        if (day == currentDay || drawableIds.size < currentDay) return
        val resources = try {
            context.packageManager.getResourcesForApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
        Executors.newSingleThreadExecutor().execute {
            val currentDayDrawable = resources.getDrawableOrNull(drawableIds[day - 1])
                    ?: return@execute
            if (currentDayDrawable is AdaptiveIconDrawable) {
                foreground = currentDayDrawable.foreground
                background = currentDayDrawable.background
                foregroundScale = 1.5f
                backgroundScale = 1.5f
            } else {
                foregroundScale = 1f
                backgroundScale = 1f
                background = null
                foreground = currentDayDrawable
            }
        }
        currentDay = day
    }
}