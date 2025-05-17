package de.mm20.launcher2.ui.launcher.scaffold

import android.app.Activity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import de.mm20.launcher2.ui.modifier.scale

/**
 * A scaffold component that finishes the activity when activated.
 */
internal class DismissComponent(private val activity: Activity): ScaffoldComponent() {
    @Composable
    override fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState
    ) {

    }

    override fun homePageModifier(
        state: LauncherScaffoldState,
        defaultModifier: Modifier
    ): Modifier {
        return defaultModifier then Modifier.scale(1f - (state.currentProgress * 0.25f)).alpha(1f - state.currentProgress)
    }

    override suspend fun onMount(state: LauncherScaffoldState) {
        super.onMount(state)
        activity.finish()
    }
}