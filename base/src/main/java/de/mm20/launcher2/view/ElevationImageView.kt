package de.mm20.launcher2.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.view.View
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import kotlin.math.max
import kotlin.math.min

class ElevationImageView : androidx.appcompat.widget.AppCompatImageView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)


    private val secondCanvas = Canvas()
    private var shadowBitmap: Bitmap? = null
    private var inAllocation: Allocation? = null
    private lateinit var renderScript : RenderScript
    private lateinit var blur : ScriptIntrinsicBlur

    val shadowPaint = Paint().also {
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        it.colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP)
        it.alpha = 66
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    val clearPaint = Paint().also {
        it.color = Color.TRANSPARENT
        it.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blur.destroy()
        renderScript.destroy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderScript = RenderScript.create(context)
        blur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
    }

    override fun onDraw(canvas: Canvas) {
        secondCanvas.drawRect(0f, 0f, secondCanvas.width.toFloat(), secondCanvas.height.toFloat(), clearPaint)

        if (drawable == null) {
            return  // couldn't resolve the URI
        }

        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight

        if (drawableWidth == 0 || drawableHeight == 0) {
            return      // nothing to draw (empty bounds)
        }

        if (shadowBitmap?.width != width || shadowBitmap?.height != height) {
            shadowBitmap?.recycle()
            inAllocation?.destroy()
            if (z > 0f && width > 0 && height > 0) {
                shadowBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    secondCanvas.setBitmap(it)
                }
                inAllocation = shadowBitmap?.let { Allocation.createFromBitmap(renderScript, it) }
            }
        }

        val saveCount = canvas.saveCount
        val saveCount2 = secondCanvas.saveCount
        canvas.save()
        secondCanvas.save()


        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        secondCanvas.translate(paddingLeft.toFloat(), paddingTop.toFloat() + getShadowTranslation())

        if (!imageMatrix.isIdentity) {
            canvas.concat(imageMatrix)
            secondCanvas.concat(imageMatrix)
        }


        drawable.draw(canvas)
        drawable.draw(secondCanvas)
        shadowBitmap = shadowBitmap?.let { blurBitmap(it) }
        canvas.restoreToCount(saveCount)
        secondCanvas.restoreToCount(saveCount2)
        shadowBitmap?.let { canvas.drawBitmap(it, 0f, 0f, shadowPaint) }
    }

    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        val alloc = inAllocation ?: return bitmap
        if (z == 0f) return bitmap
        alloc.copyFrom(bitmap)
        blur.setRadius(max(0f, min(getShadowBlurRadius(), 25f)))
        blur.setInput(alloc)
        blur.forEach(alloc)
        alloc.copyTo(bitmap)
        return bitmap
    }

    override fun setColorFilter(cf: ColorFilter?) {
        super.setColorFilter(cf)
        val drawable = drawable
        if (drawable is LottieDrawable) {
            drawable.addValueCallback(KeyPath("**"), LottieProperty.COLOR_FILTER) {
                cf
            }
        }
    }


    private fun getShadowTranslation(): Float {
        return z * 0.5f
    }

    private fun getShadowBlurRadius(): Float {
        return z * 0.5f
    }
}