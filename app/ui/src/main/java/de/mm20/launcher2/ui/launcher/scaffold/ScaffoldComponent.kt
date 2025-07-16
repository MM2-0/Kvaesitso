package de.mm20.launcher2.ui.launcher.scaffold

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier


internal abstract class ScaffoldComponent {
    /**
     * If true, the component stays open. I.e. widgets, search.
     * If false, the component is immediately dismissed after running its onMount function,
     *  returning to the home screen. I.e. turn off screen, launch app.
     */
    open val permanent: Boolean = true

    /**
     * For non-permanent components, this is the delay before the component is dismissed.
     */
    open val resetDelay: Long = 0L

    /**
     * If true, a semi-transparent background is drawn behind the page.
     */
    open val drawBackground: Boolean = true

    /**
     * Show the search bar on this component, if search bar style is hidden.
     * For other styles, the search bar is always shown.
     */
    open val showSearchBar: Boolean = true

    /**
     * Whether haptic feedback should be used when the component is activated / dismissed.
     */
    open val hapticFeedback: Boolean = true

    /**
     * Whether this component can be interacted with the IME.
     * If true, IME insets will be passed to the component.
     * If false, IME insets will be ignored, to avoid unnecessary animations.
     */
    open val hasIme: Boolean = false

    /**
     * Whether the component is scrolled all the way up
     * null, if the component does not provide content
     */
    open val isAtTop: State<Boolean?> = mutableStateOf(null)

    /**
     * Whether the component is scrolled all the way down
     * null, if the component does not provide content
     */
    open val isAtBottom: State<Boolean?> = mutableStateOf(null)

    open val reverseScrolling: Boolean = false

    @Composable
    abstract fun Component(
        modifier: Modifier,
        insets: PaddingValues,
        state: LauncherScaffoldState,
    )

    @SuppressLint("ModifierFactoryExtensionFunction")
    open fun homePageModifier(state: LauncherScaffoldState, defaultModifier: Modifier): Modifier =
        defaultModifier

    @SuppressLint("ModifierFactoryExtensionFunction")
    open fun searchBarModifier(state: LauncherScaffoldState, defaultModifier: Modifier): Modifier =
        defaultModifier

    protected var isActive by mutableStateOf(false)

    /**
     * Called when the component is about to be activated, but before the animation is completed.
     */
    open suspend fun onPreActivate(state: LauncherScaffoldState) {

    }

    /**
     * Called when the component is activated, after the animation is completed.
     */
    open suspend fun onActivate(state: LauncherScaffoldState) {
        isActive = true
    }


    /**
     * Called when the component is about to be dismissed, but before the animation is completed.
     */
    open suspend fun onPreDismiss(state: LauncherScaffoldState) {

    }

    /**
     * Called when the component is dismissed, after the animation is completed, when the component is no longer visible.
     */
    open suspend fun onDismiss(state: LauncherScaffoldState) {
        isActive = false
    }
}