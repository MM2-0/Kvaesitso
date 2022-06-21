package de.mm20.launcher2.ui.launcher.transitions

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toAndroidRectF
import com.android.launcher3.GestureNavContract
import kotlinx.coroutines.flow.MutableSharedFlow

class HomeTransitionManager {

    val currentTransition = MutableSharedFlow<HomeTransitionParams?>(1)

    private val handlers = mutableSetOf<HomeTransitionHandler>()

    fun resolve(gestureNavContract: GestureNavContract) {
        for (handler in handlers) {
            val result = handler.handle(gestureNavContract)
            if (result != null) {
                gestureNavContract.sendEndPosition(result.targetBounds.toAndroidRectF())
                currentTransition.tryEmit(result)
                return
            }
        }
        currentTransition.tryEmit(null)
    }

    fun registerHandler(handler: HomeTransitionHandler) {
        handlers.add(handler)
    }

    fun unregisterHandler(handler: HomeTransitionHandler) {
        handlers.remove(handler)
    }
}

val LocalHomeTransitionManager = compositionLocalOf<HomeTransitionManager?> { null }