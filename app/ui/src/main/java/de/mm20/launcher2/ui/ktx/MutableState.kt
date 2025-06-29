package de.mm20.launcher2.ui.ktx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.Dp

suspend fun MutableState<Dp>.animateTo(targetValue: Dp) {
    animateTo(targetValue, Dp.VectorConverter)
}

suspend fun MutableState<Float>.animateTo(
    targetValue: Float,
    animationSpec: AnimationSpec<Float> = SpringSpec(),
) {
    animateTo(targetValue, Float.VectorConverter, animationSpec)
}

suspend inline fun <T, V: AnimationVector> MutableState<T>.animateTo(
    targetValue: T,
    converter: TwoWayConverter<T, V>,
    animationSpec: AnimationSpec<T> = SpringSpec(),
) {
    val animatable = Animatable(this.value, converter)
    animatable.animateTo(targetValue, animationSpec = animationSpec) animatable@ {
        this@animateTo.value = this.value
    }
}