package de.mm20.launcher2.ui.legacy.widget

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import de.mm20.launcher2.ui.ClockWidget
import de.mm20.launcher2.ui.LegacyLauncherTheme

class ClockWidget : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    val view = ComposeView(context)

    init {
        clipToPadding = false
        clipChildren = false
        layoutTransition = LayoutTransition()

        val composeView = ComposeView(context)

        addView(composeView)

        composeView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)


        composeView.setContent {
            LegacyLauncherTheme {
                ClockWidget()
            }
        }
    }
}

