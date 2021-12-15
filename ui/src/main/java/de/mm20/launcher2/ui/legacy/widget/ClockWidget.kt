package de.mm20.launcher2.ui.legacy.widget

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.ClockWidget
import de.mm20.launcher2.ui.LegacyLauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.view.LauncherCardView
import java.util.*

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
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)


        composeView.setContent {
            val transparentBg by transparentBackgroundState.observeAsState(true)
            LegacyLauncherTheme {
                ClockWidget(transparentBackground = transparentBg)
            }
        }
    }

    private val transparentBackgroundState = MutableLiveData<Boolean>()

    var transparentBackground: Boolean = true
}

