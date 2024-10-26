package de.mm20.launcher2.graphics

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.ktx.dp
import kotlin.math.roundToInt

class BadgeDrawable(context: Context, drawable: Drawable) : Drawable() {

    private val drawable: BitmapDrawable

    init {
        val size = (28.8 * context.dp).roundToInt()
        val drw: Drawable = if (drawable is AdaptiveIconDrawable) {
            LayerDrawable(arrayOf(
                    drawable.background,
                    drawable.foreground
            )).apply {
                val inset = (-size * 0.25).roundToInt()
                setLayerInset(1, inset, inset, inset, inset)
                setLayerInset(0, inset, inset, inset, inset)
            }
        } else {
            drawable
        }
        val bitmap = drw.toBitmap(size, size).run {
            if(isMutable) this
            else this.copy(config ?: Bitmap.Config.ARGB_8888, true)
        }
        this.drawable = BitmapDrawable(context.resources, bitmap)
    }

    override fun setAlpha(alpha: Int) {
    }

    @Deprecated("Deprecated in super class")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
    }

    override fun draw(canvas: Canvas) {
        canvas.clipPath(Path().apply { addOval(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(), Path.Direction.CW) })
        drawable.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        drawable.draw(canvas)
    }
}