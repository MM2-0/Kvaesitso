package de.mm20.launcher2.ui.legacy.widget

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

abstract class LauncherWidget : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    init {
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }
    }

    abstract fun update()
    fun updateCompactView() {
        compactView?.update()
    }

    abstract val compactViewRanking: Int
    abstract val compactView: CompactView?
    abstract val settingsFragment: String?
    open val hasSettings = false
    abstract val canResize: Boolean
    abstract val name: String
    var show: Boolean = true
        set(value) {
            onVisibilityChanged?.invoke(value)
            field = value
        }

    var onVisibilityChanged: ((Boolean) -> Unit)? = null

    open fun startResize() {}
    open fun endResize() {}

    open fun openSettings() {}
}