package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.core.view.isVisible
import androidx.core.view.iterator
import de.mm20.launcher2.ktx.copyTo
import de.mm20.launcher2.ktx.scale
import de.mm20.launcher2.ktx.toRectF
import de.mm20.launcher2.ktx.translate
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.R
import kotlin.math.min

class BlurView : View {

    private val globalRect = Rect()
    private val blurPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    private val maskPaint = Paint().apply { color = Color.BLACK }
    private val viewRect = RectF()
    private val wallpaperRect = RectF()
    private val windowRect = Rect()

    private val dimWallpaper = LauncherPreferences.instance.dimWallpaper
    private val dimPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.wallpaper_dim) }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        /*val blurredWallpaper = LauncherApplication.instance.blurredWallpaper
        blurredWallpaper ?: return drawWallpaperDim(canvas)
        if (blurredWallpaper.isRecycled) return drawWallpaperDim(canvas)
        val parent = parent as? ViewGroup ?: return drawWallpaperDim(canvas)
        drawMasks(parent, canvas)
        getGlobalVisibleRect(globalRect)
        globalRect.toRectF(viewRect)
        getWindowVisibleDisplayFrame(windowRect)
        /*canvas.drawBitmap(blurredWallpaper,
                -(blurredWallpaper.width - viewRect.width()) / 2f,
                -(blurredWallpaper.height - viewRect.height()) / 2f,
                blurPaint)*/
        if (blurredWallpaper.width >= width && blurredWallpaper.height >= height) {
            viewRect.copyTo(wallpaperRect)
            wallpaperRect.translate((blurredWallpaper.width - wallpaperRect.width()) / 2f, (blurredWallpaper.height - wallpaperRect.height()) / 2f)
        } else {
            val scale = min(blurredWallpaper.width / width.toFloat(), blurredWallpaper.height / height.toFloat())
            viewRect.copyTo(wallpaperRect)
            wallpaperRect.scale(scale)
            wallpaperRect.translate((blurredWallpaper.width - wallpaperRect.width()) / 2, (blurredWallpaper.height - wallpaperRect.height()) / 2)
        }
        if (viewRect.top > 0f) {
            wallpaperRect.translate(0f, viewRect.top)
        }
        canvas.drawBitmap(blurredWallpaper, wallpaperRect.toRect(), viewRect, blurPaint)
        drawWallpaperDim(canvas)*/
    }

    private fun drawWallpaperDim(canvas: Canvas) {
        if (dimWallpaper) {
            canvas.drawRect(Rect(0, 0, canvas.width, canvas.height), dimPaint)
        }
    }

    private var viewBounds = RectF()

    private fun drawMasks(parent: ViewGroup, canvas: Canvas) {
        loop@ for (view in parent.iterator()) {
            when {
                !view.isVisible || view.alpha == 0f -> {
                }
                view is LauncherCardView -> {
                    if (view.backgroundOpacity == 0 || view.backgroundOpacity == 0xFF) {
                        continue@loop
                    }
                    if (!view.getGlobalVisibleRect(globalRect)) continue@loop
                    globalRect.toRectF(viewBounds)
                    if (viewRect.top > 0f) {
                        viewBounds.translate(0f, -viewRect.top)
                    }
                    canvas.drawRoundRect(viewBounds, view.radius, view.radius, maskPaint)
                }
                view is ViewGroup -> {
                    drawMasks(view, canvas)
                }
            }
        }
    }
}