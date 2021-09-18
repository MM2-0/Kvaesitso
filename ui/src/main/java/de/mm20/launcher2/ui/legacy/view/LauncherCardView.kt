package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.preferences.CardBackground
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.R
import kotlin.math.roundToInt

/**
 * A card view implementation that solves the following issues of MaterialCardView:
 *  (1) Content clipping in transitions
 *  (2) Elevation overlay color
 */
open class LauncherCardView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val isDarkTheme = resources.getBoolean(R.bool.is_dark_theme)


    var backgroundOpacity: Int = LauncherPreferences.instance.cardOpacity
        set(value) {
            setCardBackgroundColor(cardBackgroundColor.defaultColor.let {
                ColorStateList.valueOf((it and 0xFFFFFF) or (value shl 24))
            })
            field = value
        }

    var strokeOpacity: Int = if (LauncherPreferences.instance.cardStrokeWidth > 0) 0xFF else 0
        set(value) {
            setStrokeColor(strokeColorStateList?.defaultColor?.let {
                ColorStateList.valueOf((it and 0xFFFFFF) or (value shl 24))
            })
            field = value
        }

    init {
        val cardColor = when (LauncherPreferences.instance.cardBackground) {
            CardBackground.DEFAULT-> context.getColor(R.color.cardview_background)
            CardBackground.BLACK -> context.getColor(R.color.cardview_background_black)
        }
        setCardBackgroundColor(cardColor)
        strokeColor = cardColor
        strokeWidth = (LauncherPreferences.instance.cardStrokeWidth * dp).roundToInt()
        radius = LauncherPreferences.instance.cardRadius * dp

        context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.LauncherCardView,
                0, 0).apply {

            try {
                backgroundOpacity = getInt(R.styleable.LauncherCardView_backgroundOpacity, LauncherPreferences.instance.cardOpacity)
            } finally {
                recycle()
            }
        }
        strokeOpacity = if (backgroundOpacity == 0) 0 else 0xFF
        elevation = if (backgroundOpacity == 255) elevation else 0f
        cardElevation = elevation
    }
}