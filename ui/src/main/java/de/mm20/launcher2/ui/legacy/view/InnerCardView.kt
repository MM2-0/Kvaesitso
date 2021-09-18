package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.R

open class InnerCardView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {
    init {

        radius = LauncherPreferences.instance.cardRadius * dp
        strokeColor = ContextCompat.getColor(context, R.color.color_divider)
        strokeWidth = (1 * dp).toInt()
        cardElevation = 2 * dp
        outlineProvider = null
    }
}
