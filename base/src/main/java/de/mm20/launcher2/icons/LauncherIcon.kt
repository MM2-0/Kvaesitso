package de.mm20.launcher2.icons

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import java.lang.ref.WeakReference

open class LauncherIcon(
    foreground: Drawable,
    background: Drawable? = null,
    foregroundScale: Float = 1f,
    backgroundScale: Float = 1f,
    var autoGenerateBackgroundMode: Int = BACKGROUND_WHITE,
    val isThemeable: Boolean = false,
) {

    var foreground = foreground
        set(value) {
            field = value
            updateBackgroundColor()
            notifyCallbacks()
        }

    private fun updateBackgroundColor() {
        if (background == null) {
            when (autoGenerateBackgroundMode) {
                BACKGROUND_DYNAMIC -> {
                    val palette = Palette
                            .from(foreground.toBitmap())
                            .generate()
                    this.background = ColorDrawable(palette.getDominantColor(Color.WHITE))
                    badgeColor = palette.getLightVibrantColor(0xFFF0F0F0.toInt())
                }
                BACKGROUND_WHITE -> this.background = ColorDrawable(Color.WHITE)
                else -> this.foregroundScale = 1f
            }
        }
    }

    var background = background
        set(value) {
            field = value
            notifyCallbacks()
        }

    var foregroundScale = foregroundScale
        set(value) {
            field = value
            notifyCallbacks()
        }

    var backgroundScale = backgroundScale
        set(value) {
            field = value
            notifyCallbacks()
        }

    private val callbacks = mutableListOf<WeakReference<(LauncherIcon) -> Unit>>()

    fun registerCallback(callback: (LauncherIcon) -> Unit) {
        callbacks.add(WeakReference(callback))
    }

    protected fun notifyCallbacks() {
        val iterator = callbacks.iterator()
        while(iterator.hasNext()) {
            val callback = iterator.next()
            callback.get()?.invoke(this) ?: iterator.remove()
        }
    }

    var badgeColor: Int = 0xFFF0F0F0.toInt()

    init {
        updateBackgroundColor()
    }

    companion object {
        const val BACKGROUND_NONE = 1
        const val BACKGROUND_DYNAMIC = 0
        const val BACKGROUND_WHITE = 2
    }
}