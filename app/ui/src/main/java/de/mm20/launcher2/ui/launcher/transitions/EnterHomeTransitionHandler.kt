package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.android.launcher3.GestureNavContract

fun interface EnterHomeTransitionHandler {
    fun handle(gestureNavContract: GestureNavContract): EnterHomeTransitionParams?
}

@Composable
fun HandleEnterHomeTransition(handler: EnterHomeTransitionHandler) {
    val transitionManager = LocalEnterHomeTransitionManager.current
    DisposableEffect(handler) {
        transitionManager?.registerHandler(handler)

        onDispose {
            transitionManager?.unregisterHandler(handler)
        }
    }
}