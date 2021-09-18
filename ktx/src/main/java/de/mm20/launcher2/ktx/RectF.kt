package de.mm20.launcher2.ktx

import android.graphics.RectF

fun RectF.scale(factor: Float) {
    val newWidth = width() * factor
    val newHeight = height() * factor
    bottom += newHeight - height()
    right += newWidth - width()
}

fun RectF.translate(x: Float, y: Float): RectF {
    top += y
    bottom += y
    left += x
    right += x
    return this
}

infix fun RectF.copyTo(other: RectF) {
    other.top = top
    other.left = left
    other.right = right
    other.bottom = bottom
}