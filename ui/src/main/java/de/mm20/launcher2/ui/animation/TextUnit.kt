package de.mm20.launcher2.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

object TextUnitConverter : TwoWayConverter<TextUnit, AnimationVector2D> {
    override val convertFromVector: (AnimationVector2D) -> TextUnit
        get() = {
            TextUnit(
                it.v1,
                if (it.v2 > 0.5f) TextUnitType.Em else TextUnitType.Sp
            )
        }
    override val convertToVector: (TextUnit) -> AnimationVector2D
        get() = {
            AnimationVector(
                it.value,
                if (it.isEm) 1f else 0f
            )
        }

}


@Composable
inline fun <S> Transition<S>.animateTextUnit(
    noinline transitionSpec:
    @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<TextUnit> = { spring() },
    label: String = "ValueAnimation",
    targetValueByState: @Composable (state: S) -> TextUnit
): State<TextUnit> {
    return animateValue(
        typeConverter = TextUnitConverter,
        label = label,
        transitionSpec = transitionSpec,
        targetValueByState = targetValueByState
    )
}