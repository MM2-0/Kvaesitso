package de.mm20.launcher2.ui.launcher.transitions

import android.view.Window
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.toRectF
import com.android.launcher3.GestureNavContract
import de.mm20.launcher2.ui.launcher.scaffold.ScaffoldPage
import kotlinx.coroutines.flow.MutableSharedFlow

class EnterHomeTransitionManager {

    val currentTransition = MutableSharedFlow<EnterHomeTransition?>(1)

    private val homeHandlers = mutableSetOf<EnterHomeTransitionHandler>()
    private val secondaryHandlers = mutableSetOf<EnterHomeTransitionHandler>()

    fun resolve(gestureNavContract: GestureNavContract, window: Window, page: ScaffoldPage) {
        val handlers = if (page === ScaffoldPage.Secondary) secondaryHandlers else homeHandlers

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

    /**
     * The scaffold page that needs to be active for this handler to be considered.
     */
    fun registerHandler(handler: EnterHomeTransitionHandler, page: ScaffoldPage) {
        if (page == ScaffoldPage.Home) {
            homeHandlers.add(handler)
        } else if (page == ScaffoldPage.Secondary) {
            secondaryHandlers.add(handler)
        }
    }

    fun unregisterHandler(handler: EnterHomeTransitionHandler, page: ScaffoldPage) {
        if (page == ScaffoldPage.Home) {
            homeHandlers.remove(handler)
        } else if (page == ScaffoldPage.Secondary) {
            secondaryHandlers.remove(handler)
        }
    }
}

val LocalEnterHomeTransitionManager = compositionLocalOf<EnterHomeTransitionManager?> { null }