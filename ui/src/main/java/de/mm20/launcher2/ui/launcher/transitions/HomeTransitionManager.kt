package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRectF
import com.android.launcher3.GestureNavContract

class HomeTransitionManager {

    private val handlers = mutableSetOf<HomeTransitionHandler>()

    fun resolve(gestureNavContract: GestureNavContract) {
        for (handler in handlers) {
            val result = handler.handle(gestureNavContract)
            if (result != null) {
                gestureNavContract.sendEndPosition(result.targetBounds.toAndroidRectF())
                break
            }
        }
    }

    private fun dispatch(params: HomeTransitionParams) {

    }

    fun registerHandler(handler: HomeTransitionHandler) {
        handlers.add(handler)
    }

    fun unregisterHandler(handler: HomeTransitionHandler) {
        handlers.remove(handler)
    }
}

val LocalHomeTransitionManager = compositionLocalOf<HomeTransitionManager?> { null }