package de.mm20.launcher2.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.graphics.withScale
import de.mm20.launcher2.ktx.drawWithColorFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import palettes.TonalPalette
import java.lang.ref.WeakReference

sealed interface LauncherIcon

data class LauncherIconRenderSettings(
    val size: Int,
    val fgThemeColor: Int,
    val bgThemeColor: Int,
    val fgTone: Int,
    val bgTone: Int,
)

data class StaticLauncherIcon(
    val foregroundLayer: LauncherIconLayer,
    val backgroundLayer: LauncherIconLayer,
) : LauncherIcon {
    private var cachedBitmap: WeakReference<Bitmap>? = null
    private var cachedRenderSettings: LauncherIconRenderSettings? = null
    private var renderSemaphore = Semaphore(1)

    fun getCachedBitmap(settings: LauncherIconRenderSettings): Bitmap? {
        return if (cachedRenderSettings == settings) cachedBitmap?.get() else null
    }

    /**
     * Render this icon to a bitmap.
     */
    suspend fun render(settings: LauncherIconRenderSettings): Bitmap {
        val cachedBmp = cachedBitmap?.get()
        if (cachedRenderSettings == settings && cachedBmp != null) return cachedBmp
        val bmp = withContext(Dispatchers.Default) {
            renderSemaphore.withPermit {
                val bmp =
                    if (cachedBmp == null || cachedBmp.width != settings.size || cachedBmp.height != settings.size) {
                        Bitmap.createBitmap(settings.size, settings.size, Bitmap.Config.ARGB_8888)!!
                    } else cachedBmp
                val canvas = Canvas(bmp)
                canvas.drawRect(
                    Rect(0, 0, canvas.width, canvas.height), Paint().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    })
                renderLayer(canvas, backgroundLayer, settings.bgThemeColor, settings.bgTone)
                renderLayer(canvas, foregroundLayer, settings.fgThemeColor, settings.fgTone)
                cachedBitmap = WeakReference(bmp)
                cachedRenderSettings = settings
                bmp
            }
        }
        return bmp
    }

    private fun renderLayer(canvas: Canvas, layer: LauncherIconLayer, themeColor: Int, tone: Int) {
        when(layer) {
            is ColorLayer -> {
                val paint = Paint()
                paint.color = if (layer.color == 0) themeColor else getTone(layer.color, tone)
                canvas.drawRect(Rect(0, 0, canvas.width, canvas.height), paint)
            }
            is StaticIconLayer -> {
                canvas.withScale(
                    layer.scale,
                    layer.scale,
                    canvas.width / 2f,
                    canvas.height / 2f,
                ) {
                    layer.icon.bounds = Rect(0, 0, canvas.width, canvas.height)
                    layer.icon.draw(canvas)
                }
            }
            is TintedIconLayer -> {
                val color = if (layer.color == 0) themeColor else getTone(layer.color, tone)
                canvas.withScale(
                    layer.scale,
                    layer.scale,
                    canvas.width / 2f,
                    canvas.height / 2f,
                ) {
                    layer.icon.bounds = Rect(0, 0, canvas.width, canvas.height)
                    layer.icon.drawWithColorFilter(canvas,
                        PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                    )
                }
            }
            is BitmapLayer -> {
                // hardware() previews can later be rendered again (including inside folder grids).
                // Canvas(Bitmap) is software-only. Keep the shared GPU source intact and read back
                // just this layer for the off-main composition, rather than disabling GPU icons.
                val source = layer.bitmap
                val drawable = if (source.config == Bitmap.Config.HARDWARE) {
                    checkNotNull(source.copy(Bitmap.Config.ARGB_8888, false))
                } else source
                try {
                    val scale = minOf(canvas.width.toFloat() / drawable.width, canvas.height.toFloat() / drawable.height)
                    canvas.withScale(scale, scale, canvas.width / 2f, canvas.height / 2f) {
                        canvas.drawBitmap(drawable, canvas.width / 2f - drawable.width / 2f, canvas.height / 2f - drawable.height / 2f, null)
                    }
                } finally {
                    if (drawable !== source) drawable.recycle()
                }
            }
            is TextLayer -> {
                val paint = Paint().apply {
                    color = if (layer.color == 0) themeColor else getTone(layer.color, tone)
                    textSize = canvas.width * 0.6f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(layer.text, canvas.width / 2f, canvas.height / 2f - (paint.descent() + paint.ascent()) / 2, paint)
            }
            is GridLayer -> {
                val size = canvas.width / 2
                layer.layers.take(4).forEachIndexed { index, subLayer ->
                    val left = (index % 2) * size
                    val top = (index / 2) * size
                    canvas.save()
                    canvas.translate(left.toFloat(), top.toFloat())
                    canvas.clipRect(0, 0, size, size)
                    // We need a sub-canvas or scaling
                    canvas.scale(0.5f, 0.5f)
                    renderLayer(canvas, subLayer, themeColor, tone)
                    canvas.restore()
                }
            }
            else -> {}
        }
    }

    private fun getTone(argb: Int, tone: Int): Int {
        return TonalPalette
            .fromInt(argb)
            .tone(tone)
    }

    suspend fun hardware(): StaticLauncherIcon = withContext(Dispatchers.Default) {
        val bmp = render(LauncherIconRenderSettings(192, 0, 0, 10, 90))
        if (bmp.config != Bitmap.Config.HARDWARE) {
            val hBmp = bmp.copy(Bitmap.Config.HARDWARE, false)
            if (hBmp != null) {
                return@withContext StaticLauncherIcon(BitmapLayer(hBmp), TransparentLayer)
            }
        }
        this@StaticLauncherIcon
    }
}

interface DynamicLauncherIcon : LauncherIcon {
    suspend fun getIcon(time: Long): StaticLauncherIcon
}
