package de.mm20.launcher2.ui.ktx

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Animate between two shapes.
 * Limitations:
 *  - Only works for [RoundedCornerShape] and [CutCornerShape]
 *  - Shape type should be consistent (e.g. you can't animate between [RoundedCornerShape] and [CutCornerShape]), otherwise the animation will be incorrect
 *  - Doesn't support percentage based corner sizes
 */
@Composable
fun animateShapeAsState(
    shape: Shape,
    animationSpec: AnimationSpec<CornerBasedShape> = remember { spring() },
    visibilityThreshold: CornerBasedShape? = null,
    label: String = "ValueAnimation",
    finishedListener: ((Shape) -> Unit)? = null
): State<Shape> {

    if (shape !is CornerBasedShape) {
        return remember(shape) {
            mutableStateOf(shape)
        }
    }

    val density = LocalDensity.current
    val converter = remember(shape.javaClass, density) {
        if (shape is CutCornerShape) CutCornerShapeConverter(density)
        else RoundedCornerShapeConverter(density)
    }
    return animateValueAsState(
        shape,
        typeConverter = converter,
        animationSpec = animationSpec,
        visibilityThreshold = visibilityThreshold,
        label = label,
        finishedListener = finishedListener
    )
}

private class RoundedCornerShapeConverter(
    private val density: Density,
) : TwoWayConverter<CornerBasedShape, AnimationVector4D> {
    override val convertFromVector: (AnimationVector4D) -> CornerBasedShape
        get() = {
            RoundedCornerShape(
                topStart = it.v1,
                topEnd = it.v2,
                bottomEnd = it.v3,
                bottomStart = it.v4
            )
        }
    override val convertToVector: (CornerBasedShape) -> AnimationVector4D
        get() = {
            AnimationVector4D(
                it.topStart.toPx(Size.Zero, density),
                it.topEnd.toPx(Size.Zero, density),
                it.bottomEnd.toPx(Size.Zero, density),
                it.bottomStart.toPx(Size.Zero, density)
            )
        }

}

private class CutCornerShapeConverter(
    private val density: Density,
) : TwoWayConverter<CornerBasedShape, AnimationVector4D> {
    override val convertFromVector: (AnimationVector4D) -> CornerBasedShape
        get() = {
            CutCornerShape(
                topStart = it.v1,
                topEnd = it.v2,
                bottomEnd = it.v3,
                bottomStart = it.v4
            )
        }
    override val convertToVector: (CornerBasedShape) -> AnimationVector4D
        get() = {
            AnimationVector4D(
                it.topStart.toPx(Size.Zero, density),
                it.topEnd.toPx(Size.Zero, density),
                it.bottomEnd.toPx(Size.Zero, density),
                it.bottomStart.toPx(Size.Zero, density)
            )
        }

}

fun CornerBasedShape.withCorners(
    topStart: Boolean = true,
    topEnd: Boolean = true,
    bottomEnd: Boolean = true,
    bottomStart: Boolean = true
): Shape {
    if (topStart && topEnd && bottomEnd && bottomStart) return this
    if (!topStart && !topEnd && !bottomEnd && !bottomStart) return RectangleShape
    return copy(
        topStart = if (topStart) this.topStart else CornerSize(0f),
        topEnd = if (topEnd) this.topEnd else CornerSize(0f),
        bottomEnd = if (bottomEnd) this.bottomEnd else CornerSize(0f),
        bottomStart = if (bottomStart) this.bottomStart else CornerSize(0f)
    )
}