package de.mm20.launcher2.ktx

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable

fun LayerDrawable.getDrawableOrNull(index: Int): Drawable? {
    return try {
        this.getDrawable(index)
    } catch (e: IndexOutOfBoundsException) {
        return null
    }
}