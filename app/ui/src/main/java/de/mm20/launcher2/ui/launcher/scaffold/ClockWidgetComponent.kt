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
}