package de.mm20.launcher2.graphics

import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable

class TextDrawable(
    val text: String,
    val color: Int = Color.WHITE,
    fontSize: Float = 13f,
    typeface: Typeface = Typeface.DEFAULT
): Drawable() {

    private val paint = Paint()
    private val rect = Rect()

    init {
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = fontSize
        paint.color = color
        paint.isAntiAlias = true
        paint.typeface = typeface
        paint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        paint.getTextBounds(text, 0, text.length, rect)
        canvas.drawText(text, bounds.exactCenterX(), bounds.exactCenterY() + rect.height() / 2f, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setTintList(tint: ColorStateList?) {
        paint.color = tint?.defaultColor ?: color
    }
}