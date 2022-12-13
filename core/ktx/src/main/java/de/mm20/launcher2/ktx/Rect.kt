package de.mm20.launcher2.ktx

import android.graphics.Rect
import android.graphics.RectF

fun Rect.translate(x: Int, y: Int): Rect {
    top += y
    bottom += y
    left += x
    right += x
    return this
}

fun Rect.toRectF(other: RectF) {
    other.left = left.toFloat()
    other.bottom = bottom.toFloat()
    other.right = right.toFloat()
    other.top = top.toFloat()
}
