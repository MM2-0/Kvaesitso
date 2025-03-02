package de.mm20.launcher2.ui.launcher.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

typealias ComponentContent = @Composable (modifier: Modifier, insets: PaddingValues, progress: Float) -> Unit

interface ScaffoldComponent {
    /**
     * If true, the component stays open. I.e. widgets, search.
     * If false, the component is immediately dismissed after running its onMount function,
     *  returning to the home screen. I.e. turn off screen, launch app.
     */
    val permanent: Boolean
        get() = true

    val content: ComponentContent

    /**
     * Called when the component is mounted, after the animation is completed.
     */
    fun onMount(): Unit = Unit

    /**
     * Called when the component is unmounted, after the animation is completed, when the component is no longer visible.
     */
    fun onUnmount(): Unit = Unit
}