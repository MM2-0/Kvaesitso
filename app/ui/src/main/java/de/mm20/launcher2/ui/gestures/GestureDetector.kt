package de.mm20.launcher2.ui.gestures

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

class GestureDetector {
    private var dragStart: Offset? = null
    private var currentDrag : Offset? = null

    var gestureListener: OnGestureListener? = null

    fun dispatchDoubleTap(position: Offset) {
        gestureListener?.onDoubleTap(position)
    }

    fun dispatchLongPress(position: Offset) {
        gestureListener?.onLongPress(position)
    }

    private var hasDragEnded = false
    fun dispatchDrag(offset: Offset) {
        if (hasDragEnded) return
        val totalDrag = currentDrag?.plus(offset) ?: offset
        currentDrag = totalDrag
        if (gestureListener?.onDrag(totalDrag) == true) hasDragEnded = true
    }

    fun dispatchDragEnd() {
        dragStart = null
        currentDrag = null
        hasDragEnded = false
    }


    interface OnGestureListener {
        fun onDoubleTap(position: Offset) {}
        fun onLongPress(position: Offset) {}

        /**
         * @return true if the drag gesture has been handled.
         * The gesture detector will no longer track the drag gesture in this case.
         */
        fun onDrag(offset: Offset): Boolean = false
    }
}

val LocalGestureDetector = staticCompositionLocalOf<GestureDetector> {
    GestureDetector()
}