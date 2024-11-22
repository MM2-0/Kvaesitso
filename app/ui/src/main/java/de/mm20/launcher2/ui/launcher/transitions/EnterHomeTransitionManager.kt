package de.mm20.launcher2.ui.launcher.transitions

import android.view.Window
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.toRectF
import com.android.launcher3.GestureNavContract
import kotlinx.coroutines.flow.MutableSharedFlow

class EnterHomeTransitionManager {

    val currentTransition = MutableSharedFlow<EnterHomeTransition?>(1)

    private val handlers = mutableSetOf<EnterHomeTransitionHandler>()

    fun resolve(gestureNavContract: GestureNavContract, window: Window) {
        for (handler in handlers) {
            val result = handler.handle(gestureNavContract)
            if (result != null) {
                val startRect = IntRect(
                    IntOffset.Zero,
                    IntSize(window.decorView.width, window.decorView.height)
                )
                val targetBounds = result.targetBounds
                gestureNavContract.sendEndPosition(targetBounds.toAndroidRect().toRectF())
                currentTransition.tryEmit(
                    EnterHomeTransition(
                        startBounds = startRect,
                        icon = result.icon,
                        targetBounds = targetBounds,
                    )
                )
                return
            }
        }
        currentTransition.tryEmit(EnterHomeTransition())
    }

    fun clear() {
        currentTransition.tryEmit(null)
    }

    fun registerHandler(handler: EnterHomeTransitionHandler) {
        handlers.add(handler)
    }

    fun unregisterHandler(handler: EnterHomeTransitionHandler) {
        handlers.remove(handler)
    }
}

val LocalEnterHomeTransitionManager = compositionLocalOf<EnterHomeTransitionManager?> { null }