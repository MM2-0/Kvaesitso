package de.mm20.launcher2.ui.gestures

import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

class GestureManager {
    private var dragStart: Offset? = null
    private var currentDrag : Offset? = null

    fun reportDoubleTap(position: Offset) {
        Log.d("MM20", "double tap: $position")
    }

    fun reportLongPress(position: Offset) {
        Log.d("MM20", "long press: $position")
    }

    fun reportDrag(offset: Offset) {
    }

    fun reportDragEnd() {
        dragStart = null
        currentDrag = null
    }
}

val LocalGestureManager = staticCompositionLocalOf<GestureManager> {
    GestureManager()
}