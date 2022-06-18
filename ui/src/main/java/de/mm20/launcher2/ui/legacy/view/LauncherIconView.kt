package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import de.mm20.launcher2.icons.LauncherIcon
import org.koin.core.component.KoinComponent

class LauncherIconView : View, KoinComponent {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    var icon: LauncherIcon? = null
        set(value) {
            field = value
            invalidate()
        }
}

