package de.mm20.launcher2.ui.modifier

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

@Stable
fun Modifier.scale(
    scaleX: Float, scaleY: Float, transformOrigin: TransformOrigin
) = if (scaleX != 1.0f || scaleY != 1.0f) {
    graphicsLayer(
        scaleX = scaleX,
        scaleY = scaleY,
        transformOrigin = transformOrigin
    )
} else {
    this
}

fun Modifier.scale(
    scale: Float,
    transformOrigin: TransformOrigin
) = scale(scale, scale, transformOrigin)