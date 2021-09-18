package de.mm20.launcher2.badges

import android.graphics.drawable.Drawable

data class Badge(
        var number: Int? = null,
        var progress: Float? = null,
        var iconRes: Int? = null,
        var icon: Drawable? = null
)