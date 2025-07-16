package de.mm20.launcher2.ui.theme.shapes

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.themes.shapes.CornerStyle
import de.mm20.launcher2.themes.shapes.Shape as ThemeShape
import de.mm20.launcher2.themes.shapes.Shapes as ThemeShapes

@Composable
fun shapesOf(shapes: ThemeShapes): Shapes {
    return remember(shapes) {
        Shapes(
            extraSmall = fromShape(shapes.extraSmall, shapes.baseShape, 1f / 3f),
            small = fromShape(shapes.small, shapes.baseShape, 2f / 3f),
            medium = fromShape(shapes.medium, shapes.baseShape, 1f),
            large = fromShape(shapes.large, shapes.baseShape, 4f / 3f),
            largeIncreased = fromShape(shapes.largeIncreased, shapes.baseShape, 5f / 3f),
            extraLarge = fromShape(shapes.extraLarge, shapes.baseShape, 7f / 3f),
            extraLargeIncreased = fromShape(shapes.extraLargeIncreased, shapes.baseShape, 8f / 3f),
            extraExtraLarge = fromShape(shapes.extraExtraLarge, shapes.baseShape, 12f / 3f),
        )
    }
}

private fun fromShape(shape: ThemeShape?, baseShape: ThemeShape, factor: Float): CornerBasedShape {
    val topStart = getCornerRadius(shape, baseShape, factor, 0)
    val topEnd = getCornerRadius(shape, baseShape, factor, 1)
    val bottomEnd = getCornerRadius(shape, baseShape, factor, 2)
    val bottomStart = getCornerRadius(shape, baseShape, factor, 3)

    return if ((shape?.corners ?: baseShape.corners) == CornerStyle.Cut) {
        CutCornerShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart
        )
    } else {
        RoundedCornerShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart
        )
    }
}

private fun getCornerRadius(
    shape: ThemeShape?,
    baseShape: ThemeShape,
    factor: Float,
    index: Int
): CornerSize {
    return CornerSize(
        (shape?.radii?.get(index)?.toFloat() ?: ((baseShape.radii?.get(index)?.toFloat()
            ?: 12f) * factor)).dp
    )
}