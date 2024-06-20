package de.mm20.launcher2.ui.component

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeAnimationMode.Companion.Immediately
import androidx.compose.foundation.MarqueeDefaults
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import de.mm20.launcher2.ui.ktx.conditional
import de.mm20.launcher2.ui.ktx.drawFadedEdge

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    iterations: Int = MarqueeDefaults.Iterations,
    animationMode: MarqueeAnimationMode = Immediately,
    repeatDelayMillis: Int = MarqueeDefaults.RepeatDelayMillis,
    initialDelayMillis: Int = if (animationMode == Immediately) repeatDelayMillis else 0,
    spacing: MarqueeSpacing = MarqueeDefaults.Spacing,
    velocity: Dp = MarqueeDefaults.Velocity,
    fadeLeft: Dp? = null,
    fadeRight: Dp? = null,
) {
    var textSize by remember { mutableIntStateOf(0) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        style = style,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        softWrap = false,
        maxLines = 1,
        onTextLayout = { textLayout = it },
        modifier = modifier
            .onGloballyPositioned { textSize = it.size.width }
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .conditional(
                textLayout != null && textLayout!!.size.width > textSize,
                Modifier
                    .drawWithContent {
                        drawContent()
                        if (fadeLeft != null) {
                            drawFadedEdge(leftEdge = true, fadeLeft)
                        }
                        if (fadeRight != null) {
                            drawFadedEdge(leftEdge = false, fadeRight)
                        }
                    }
            )
            .basicMarquee(
                iterations = iterations,
                initialDelayMillis = initialDelayMillis,
                spacing = spacing,
                repeatDelayMillis = repeatDelayMillis,
                velocity = velocity
            )
    )
}