package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.android.launcher3.GestureNavContract
import de.mm20.launcher2.ui.launcher.scaffold.LocalScaffoldPage

fun interface EnterHomeTransitionHandler {
    fun handle(gestureNavContract: GestureNavContract): EnterHomeTransitionParams?
}

@Composable
fun HandleEnterHomeTransition(handler: EnterHomeTransitionHandler) {
    val transitionManager = LocalEnterHomeTransitionManager.current
    val page = LocalScaffoldPage.current
    if (page != null && transitionManager != null) {
        DisposableEffect(handler, page) {
            transitionManager.registerHandler(handler, page)

            onDispose {
                transitionManager.unregisterHandler(handler, page)
            }
        }
    }
}