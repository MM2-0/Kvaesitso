package de.mm20.launcher2.ui.animation

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextGeometricTransform

@Composable
fun animateTextStyleAsState(
    textStyle: TextStyle
): State<TextStyle> {
    val state = remember { mutableStateOf(textStyle) }

    val transition = updateTransition(textStyle, label = "animateTextStyleAsState")

    val color by transition.animateColor(label = "color") {
        it.color
    }
    val fontWeight by transition.animateInt(label = "fontWeight") {
        it.fontWeight?.weight ?: 400
    }
    val fontSize by transition.animateTextUnit {
        it.fontSize
    }
    val letterSpacing by transition.animateTextUnit {
        it.letterSpacing
    }
    val baselineShift by transition.animateFloat(label = "baselineShift") {
        it.baselineShift?.multiplier ?: 0f
    }
    val background by transition.animateColor(label = "background") {
        it.background
    }
    val lineHeight by transition.animateTextUnit {
        it.lineHeight
    }


    state.value = textStyle.copy(
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight(fontWeight),
        letterSpacing = letterSpacing,
        baselineShift = BaselineShift(baselineShift),
        background = background,
        lineHeight = lineHeight,
    )

    return state
}