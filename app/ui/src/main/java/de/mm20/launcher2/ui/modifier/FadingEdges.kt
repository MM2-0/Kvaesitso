package de.mm20.launcher2.ui.modifier

import androidx.annotation.FloatRange
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

fun Modifier.verticalFadingEdges(
    enabled: Boolean = true,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    /**
     * How strong the fading effect should be.
     * If 1, edges will be completely transparent.
     * If 0, the modifier will have no effect at all.
     */
    @FloatRange(from = 0.0, to = 1.0, fromInclusive = true, toInclusive = true) amount: Float = 1f
): Modifier {
    if(!enabled) return this
    if (top == 0.dp && bottom == 0.dp) return this

    return drawWithContent {

        val topColors = if (top > 0.dp) createColors(
            1f - amount,
            top.roundToPx() + 1,
        ) else emptyList()
        val bottomColors = if (bottom > 0.dp) createColors(
            1f - amount,
            bottom.roundToPx() + 1,
            reverse = true
        ) else emptyList()

        val topSteps =  if (top > 0.dp) createColorSteps(
            size.height,
            top.toPx() * 1.3f,
            top.roundToPx() + 1
        ) else emptyList()
        val bottomSteps =  if (bottom > 0.dp) createColorSteps(
            size.height,
            bottom.toPx() * 1.3f,
            bottom.roundToPx() + 1,
            reverse = true
        ) else emptyList()

        val paint = Paint().apply {
            blendMode = BlendMode.DstIn
            shader = LinearGradientShader(
                Offset.Zero,
                Offset(0f, size.height),
                colors = topColors + bottomColors,
                colorStops = topSteps + bottomSteps
            )
        }
        drawContent()
        drawIntoCanvas {
            it.drawRect(
                Rect(0f, 0f, size.width, size.height),
                paint
            )
        }
    }
}

private fun createColors(alpha: Float, steps: Int, reverse: Boolean = false): List<Color> {
    val interval = 1f / (steps - 1)
    return (0 until steps).map {
        val x = interval * if (reverse) (steps - 1 - it) else it
        val y = (1 - alpha) * (1f - (x - 1f).pow(2)) + alpha
        Color.Black.copy(alpha = y)
    }
}

private fun createColorSteps(height: Float, size: Float, steps: Int, reverse: Boolean = false): List<Float> {
    val interval = 1f / (steps - 1)
    return (0 until steps).map {
        val x = interval * if (reverse) (steps - 1 - it) else it
        if (reverse) 1  - (x * size / height) else (x * size / height)
    }
}