package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

typealias ComponentContent = @Composable (
    modifier: Modifier,
    insets: PaddingValues,
    animationStyle: ScaffoldAnimation?,
    animationProgress: Float
) -> Unit

internal interface ScaffoldComponent {
    /**
     * If true, the component stays open. I.e. widgets, search.
     * If false, the component is immediately dismissed after running its onMount function,
     *  returning to the home screen. I.e. turn off screen, launch app.
     */
    val permanent: Boolean
        get() = true

    /**
     * For non-permanent components, this is the delay before the component is dismissed.
     */
    val resetDelay: Long
        get() = 0L

    /**
     * If true, a semi-transparent background is drawn behind the page.
     */
    val drawBackground: Boolean
        get() = true

    /**
     * Show the search bar on this component, if search bar style is hidden.
     * For other styles, the search bar is always shown.
     */
    val showSearchBar: Boolean
        get() = true

    /**
     * Whether haptic feedback should be used when the component is activated / dismissed.
     */
    val hapticFeedback: Boolean
        get() = true

    @Composable fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    )

    @SuppressLint("ModifierFactoryExtensionFunction")
    fun homePageModifier(state: LauncherScaffoldState, defaultModifier: Modifier): Modifier = defaultModifier

    @SuppressLint("ModifierFactoryExtensionFunction")
    fun searchBarModifier(state: LauncherScaffoldState, defaultModifier: Modifier): Modifier = defaultModifier

    /**
     * Called when the component is mounted, after the animation is completed.
     */
    suspend fun onMount(state: LauncherScaffoldState): Unit = Unit

    /**
     * Called when the component is unmounted, after the animation is completed, when the component is no longer visible.
     */
    suspend fun onUnmount(state: LauncherScaffoldState): Unit = Unit
}