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
            else -> {}
        }
    }

    private fun getTone(argb: Int, tone: Int): Int {
        return TonalPalette
            .fromInt(argb)
            .tone(tone)
    }
}

interface DynamicLauncherIcon : LauncherIcon {
    suspend fun getIcon(time: Long): StaticLauncherIcon
}