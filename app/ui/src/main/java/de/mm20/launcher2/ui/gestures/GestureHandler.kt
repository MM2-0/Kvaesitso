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
        }
        onDispose {
            detector.gestureListener = null
        }
    }
}