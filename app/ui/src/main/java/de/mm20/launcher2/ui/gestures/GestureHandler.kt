package de.mm20.launcher2.ui.gestures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset

@Composable
fun GestureHandler(
    detector: GestureDetector,
    onTap: (Offset) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
    onDoubleTap: (Offset) -> Unit = {},
    onDrag: (Offset) -> Boolean = { false },
    onDragEnd: () -> Unit = {},
    onHomeButtonPress: () -> Unit = {},
) {
    DisposableEffect(detector) {
        detector.gestureListener = object : GestureDetector.OnGestureListener {
            override fun onTap(position: Offset) {
                onTap(position)
            }

            override fun onLongPress(position: Offset) {
                onLongPress(position)
            }

            override fun onDoubleTap(position: Offset) {
                onDoubleTap(position)
            }

            override fun onDrag(offset: Offset): Boolean {
                return onDrag(offset)
            }

            override fun onDragEnd() {
                onDragEnd()
            }

            override fun onHomeButtonPress() {
                onHomeButtonPress()
            }
        }
        onDispose {
            detector.gestureListener = null
        }
    }
}