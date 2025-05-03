package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.mm20.launcher2.ui.launcher.widgets.clock.ClockWidget

internal object ClockWidgetComponent : ScaffoldComponent {
    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    ) {
        ClockWidget(
            modifier = modifier,
            fillScreenHeight = true,
        )
    }
}