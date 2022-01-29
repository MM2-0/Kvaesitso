package de.mm20.launcher2.icons

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*
import kotlin.math.roundToInt

class ClockDynamicLauncherIcon(
    foreground: LayerDrawable,
    background: Drawable?,
    foregroundScale: Float,
    backgroundScale: Float,
    val hourLayer: Int,
    val minuteLayer: Int,
    val secondLayer: Int
) : DynamicLauncherIcon(
    foreground,
    background,
    foregroundScale,
    backgroundScale
) {


    init {
        foreground.also {
            try {
                it.setDrawable(secondLayer, ColorDrawable(0))
            } catch (e: IndexOutOfBoundsException) {}
            (it.getDrawable(hourLayer) as? RotateDrawable)?.fromDegrees = 0f
            (it.getDrawable(hourLayer) as? RotateDrawable)?.toDegrees = 360f
            (it.getDrawable(minuteLayer) as? RotateDrawable)?.fromDegrees = 0f
            (it.getDrawable(minuteLayer) as? RotateDrawable)?.toDegrees = 360f
        }
    }

    override fun update(context: Context) {
        val calendar = Calendar.getInstance()
        val hourDegrees = calendar[Calendar.HOUR] / 12f * 10000 + calendar[Calendar.MINUTE] / 60f * 10000f / 12
        val minuteDegrees = calendar[Calendar.MINUTE] / 60f * 10000
        (foreground as LayerDrawable).also {
            (it.getDrawable(hourLayer) as? RotateDrawable)?.level = hourDegrees.roundToInt()
            (it.getDrawable(minuteLayer) as? RotateDrawable)?.level = minuteDegrees.roundToInt()
        }
        notifyCallbacks()
    }
}