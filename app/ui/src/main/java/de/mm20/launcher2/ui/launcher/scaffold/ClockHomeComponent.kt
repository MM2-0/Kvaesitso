package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import de.mm20.launcher2.ui.launcher.widgets.clock.ClockWidget

internal object ClockHomeComponent : ScaffoldComponent() {

    override val drawBackground: Boolean = false

    override var isAtTop: State<Boolean?> = mutableStateOf(true)
    override var isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    ) {
        ClockWidget(
            modifier = modifier
                .padding(insets)
                .pointerInput(Unit) {},
            fillScreenHeight = true,
        )
    }
}