package de.mm20.launcher2.icons

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import de.mm20.launcher2.ktx.getDrawableOrNull
import java.util.*
import java.util.concurrent.Executors

class ThemedCalendarDynamicLauncherIcon(
    foregroundScale: Float,
    val packageName: String,
    val foregroundIds: IntArray,
    val foregroundTint: Int,
    background: Drawable,
) : DynamicLauncherIcon(
    foreground = ColorDrawable(0),
    background = background,
    foregroundScale = foregroundScale,
    backgroundScale = 1f,
        /** Not needed, we already have a background **/
    BACKGROUND_WHITE
) {

    var currentDay = 0
    override fun update(context: Context) {
        val calendar = Calendar.getInstance()
        val day = calendar[Calendar.DAY_OF_MONTH]
        if (day == currentDay || foregroundIds.size < currentDay) return
        val resources = try {
            context.packageManager.getResourcesForApplication(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }
        Executors.newSingleThreadExecutor().execute {
            val currentDayDrawable = resources.getDrawableOrNull(foregroundIds[day - 1])
                    ?: return@execute
            currentDayDrawable.setTint(foregroundTint)
            foreground = currentDayDrawable
        }
        currentDay = day
    }
}