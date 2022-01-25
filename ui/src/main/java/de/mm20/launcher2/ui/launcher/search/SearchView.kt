package de.mm20.launcher2.ui.launcher.search

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.ui.databinding.ViewSearchBinding

class SearchView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    val binding = ViewSearchBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        layoutTransition = ChangingLayoutTransition()
    }
}