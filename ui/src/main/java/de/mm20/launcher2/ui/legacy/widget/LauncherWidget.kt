package de.mm20.launcher2.ui.legacy.widget

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

abstract class LauncherWidget : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    abstract val canResize: Boolean
    abstract val name: String

}