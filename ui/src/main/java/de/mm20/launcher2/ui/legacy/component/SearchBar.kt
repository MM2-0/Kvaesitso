package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.ui.LegacyLauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.SearchBar
import de.mm20.launcher2.ui.launcher.search.SearchBarLevel

class SearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialCardViewStyle
) : FrameLayout(context, attrs, defStyleAttr) {

    var level: SearchBarLevel = SearchBarLevel.Resting
        set(value) {
            levelState.value = value
            field = value
        }

    private val levelState = MutableLiveData(level)

    var onFocus: (() -> Unit)? = null

    init {
        val view = ComposeView(context)
        view.setContent {
            val level by levelState.observeAsState(SearchBarLevel.Resting)
            LegacyLauncherTheme {
                Box(contentAlignment = Alignment.TopCenter) {
                    SearchBar(
                        level,
                        onFocus = { onFocus?.invoke() }
                    )
                }
            }
        }
        addView(view)
    }
}