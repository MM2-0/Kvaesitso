package de.mm20.launcher2.ui.launcher.scaffold

import android.util.Log
import androidx.compose.runtime.Composable
import de.mm20.launcher2.ui.launcher.widgets.clock.ClockWidget

class ClockWidgetComponent : ScaffoldComponent {
    override val content: ComponentContent = @Composable { modifier, insets, progress ->
        ClockWidget(
            modifier = modifier,
            fillScreenHeight = true,
        )
    }

    override fun onMount() {
        super.onMount()
        Log.d("MM20", "ClockWidgetComponent onMount")
    }

    override fun onUnmount() {
        super.onUnmount()
        Log.d("MM20", "ClockWidgetComponent onUnmount")
    }
}