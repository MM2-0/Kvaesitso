package de.mm20.launcher2.ui.launcher.transitions

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.android.launcher3.GestureNavContract

fun interface HomeTransitionHandler {
    fun handle(gestureNavContract: GestureNavContract): HomeTransitionParams?
}

@Composable
fun HandleHomeTransition(handler: HomeTransitionHandler) {
    val transitionManager = LocalHomeTransitionManager.current
    DisposableEffect(null) {
        transitionManager?.registerHandler(handler)

        onDispose {
            transitionManager?.unregisterHandler(handler)
        }
    }
}