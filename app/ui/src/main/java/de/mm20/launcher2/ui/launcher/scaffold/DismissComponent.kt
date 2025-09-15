package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

/**
 * A scaffold component that finishes the activity when activated.
 */
internal class DismissComponent(private val activity: Activity) : ScaffoldComponent() {

    override val drawBackground: Boolean = false

    override val isAtTop: State<Boolean?> = mutableStateOf(true)
    override val isAtBottom: State<Boolean?> = mutableStateOf(true)

    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun homePageModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return Modifier.alpha(1f - state.currentProgress) then defaultModifier then Modifier.scale(
            1f - (state.currentProgress * 0.25f)
        )
    }

    @SuppressLint("ModifierFactoryExtensionFunction")
    override fun searchBarModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return defaultModifier.alpha(1f - state.currentProgress)
    }

    override suspend fun onActivate(state: LauncherScaffoldState) {
        super.onActivate(state)
        activity.finish()
    }
}