package de.mm20.launcher2.ktx

import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.annotation.DrawableRes

fun TextView.setStartCompoundDrawable(drawable: Drawable?) {
    setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
}

fun TextView.setStartCompoundDrawable(@DrawableRes drawableRes: Int) {
    setCompoundDrawablesRelativeWithIntrinsicBounds(drawableRes, 0, 0, 0)
}