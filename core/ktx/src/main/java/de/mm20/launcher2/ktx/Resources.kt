package de.mm20.launcher2.ktx

import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.drawable.Drawable

fun Resources.getIntArrayOrNull(id: Int): IntArray? {
    return try {
        getIntArray(id)
    } catch (e: Resources.NotFoundException) {
        null
    }
}

fun Resources.getDrawableOrNull(id: Int, theme: Resources.Theme? = null): Drawable? {
    return try {
        getDrawable(id, theme)
    } catch (e: Resources.NotFoundException) {
        null
    }
}

fun Resources.obtainTypedArrayOrNull(id: Int): TypedArray? {
    return try {
        obtainTypedArray(id)
    } catch (e: Resources.NotFoundException) {
        null
    }
}