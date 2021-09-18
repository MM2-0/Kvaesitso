package de.mm20.launcher2.ui.legacy.helper

import android.graphics.*
import android.util.LruCache
import androidx.annotation.MainThread

/**
 * Helper object to store temporary bitmaps to draw on so they don't have to be allocated every
 * time and can be reused by different classes and methods.
 */
object BitmapHolder {
    private val cache = LruCache<Int, Pair<Bitmap, Canvas>>(8)

    @MainThread
    fun getBitmapAndCanvas(size: Int): Pair<Bitmap, Canvas> {
        return cache[size]?.apply { second.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) }
                ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).let {
                    Pair(it, Canvas(it)).also { pair -> cache.put(size, pair) }
                }
    }

}