package de.mm20.launcher2.ui.gestures

import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

class GestureManager {
    private var dragStart: Offset? = null
    private var currentDrag : Offset? = null

    fun reportDoubleTap(position: Offset) {
    }

    fun reportLongPress(position: Offset) {
    }

    fun reportDrag(offset: Offset) {
        currentDrag = (currentDrag ?: Offset.Zero) + offset
    }

    fun reportDragEnd() {
        dragStart = null
        currentDrag = null
    }
}

val LocalGestureManager = staticCompositionLocalOf<GestureManager> {
    GestureManager()
}