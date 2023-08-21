package de.mm20.launcher2.ui.component.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.ktx.hct
import de.mm20.launcher2.ui.ktx.toHexString
import hct.Hct
import kotlin.math.atan2
import kotlin.math.roundToInt

@Stable
class HctColorPickerState(
    initialColor: Color,
    val onColorChanged: (Color) -> Unit,
) {
    var hue by mutableStateOf(0f)
    var chroma by mutableStateOf(0f)
    var tone by mutableStateOf(0f)

    val color by derivedStateOf {
        Color.hct(hue, chroma, tone)
    }

    internal fun setHue(hue: Float) {
        this.hue = hue
        onColorChanged(Color.hct(hue, chroma, tone))
    }

    internal fun setChroma(sat: Float) {
        this.chroma = sat
        onColorChanged(Color.hct(hue, sat, tone))
    }

    internal fun setTone(value: Float) {
        this.tone = value
        onColorChanged(Color.hct(hue, chroma, value))
    }

    internal fun setColor(color: Color) {
        val hct = Hct.fromInt(color.toArgb())
        this.hue = hct.hue.toFloat()
        this.chroma = hct.chroma.toFloat()
        this.tone = hct.tone.toFloat()
        onColorChanged(color)
    }

    init {
        val hct = Hct.fromInt(initialColor.toArgb())
        this.hue = hct.hue.toFloat()
        this.chroma = hct.chroma.toFloat()
        this.tone = hct.tone.toFloat()
    }
}

@Composable
fun rememberHctColorPickerState(
    initialColor: Color,
    onColorChanged: (Color) -> Unit
): HctColorPickerState {
    return remember {
        HctColorPickerState(initialColor, onColorChanged)
    }
}

@Composable
fun HctColorPicker(
    state: HctColorPickerState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 32.dp)
                .aspectRatio(1f)
        ) {
            val width = this.maxWidth
            val height = this.maxHeight
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, it ->
                                val (x, y) = change.position
                                val angle = atan2(
                                    y.toDouble() - height.toPx() / 2,
                                    x.toDouble() - width.toPx() / 2,
                                )
                                val h = (Math.toDegrees(angle) + 360f) % 360f
                                state.setHue(h.toFloat())
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            val (x, y) = it
                            val angle = atan2(
                                y.toDouble() - height.toPx() / 2,
                                x.toDouble() - width.toPx() / 2,
                            )
                            val h = (Math.toDegrees(angle) + 360f) % 360f
                            state.setHue(h.toFloat())

                        }
                    }
                    .padding(8.dp)
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.hct(0f, state.chroma, state.tone),
                            Color.hct(60f, state.chroma, state.tone),
                            Color.hct(120f, state.chroma, state.tone),
                            Color.hct(180f, state.chroma, state.tone),
                            Color.hct(240f, state.chroma, state.tone),
                            Color.hct(300f, state.chroma, state.tone),
                            Color.hct(360f, state.chroma, state.tone),
                        )
                    ),
                    style = Stroke(20.dp.toPx())
                )
                drawCircle(
                    color = state.color,
                    style = Fill,
                    center = center,
                    radius = size.minDimension / 2 - 18.dp.toPx()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(state.hue),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .shadow(1.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .align(AbsoluteAlignment.CenterRight)
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(
                text = "C",
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = state.chroma,
                valueRange = 0f..150f,
                onValueChange = {
                    state.setChroma(it)
                },
                track = {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    ) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.hct(state.hue, 0f, state.tone),
                                    Color.hct(state.hue, 10f, state.tone),
                                    Color.hct(state.hue, 20f, state.tone),
                                    Color.hct(state.hue, 30f, state.tone),
                                    Color.hct(state.hue, 40f, state.tone),
                                    Color.hct(state.hue, 50f, state.tone),
                                    Color.hct(state.hue, 60f, state.tone),
                                    Color.hct(state.hue, 70f, state.tone),
                                    Color.hct(state.hue, 80f, state.tone),
                                    Color.hct(state.hue, 90f, state.tone),
                                    Color.hct(state.hue, 100f, state.tone),
                                    Color.hct(state.hue, 110f, state.tone),
                                    Color.hct(state.hue, 120f, state.tone),
                                    Color.hct(state.hue, 130f, state.tone),
                                    Color.hct(state.hue, 140f, state.tone),
                                    Color.hct(state.hue, 150f, state.tone)
                                )
                            ),
                            style = Fill,
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                    }
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp, horizontal = 8.dp)
                            .size(16.dp)
                            .shadow(1.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            )
            Text(
                text = state.chroma.roundToInt().toString(),
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "T",
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = state.tone,
                onValueChange = {
                    state.setTone(it)
                },
                valueRange = 0f..100f,
                track = {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    ) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.hct(state.hue, state.chroma, 0f),
                                    Color.hct(state.hue, state.chroma, 10f),
                                    Color.hct(state.hue, state.chroma, 20f),
                                    Color.hct(state.hue, state.chroma, 30f),
                                    Color.hct(state.hue, state.chroma, 40f),
                                    Color.hct(state.hue, state.chroma, 50f),
                                    Color.hct(state.hue, state.chroma, 60f),
                                    Color.hct(state.hue, state.chroma, 70f),
                                    Color.hct(state.hue, state.chroma, 80f),
                                    Color.hct(state.hue, state.chroma, 90f),
                                    Color.hct(state.hue, state.chroma, 100f)
                                )
                            ),
                            style = Fill,
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                    }
                },
                thumb = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp, horizontal = 8.dp)
                            .size(16.dp)
                            .shadow(1.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            )
            Text(
                text = state.tone.roundToInt().toString(),
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }

        var hexValue by remember(state.color) {
            mutableStateOf(
                state.color.toHexString().substring(1)
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .padding(horizontal = 48.dp),
            value = hexValue,
            onValueChange = {
                if (Regex("[0-9a-fA-F]{0,6}").matches(it)) {
                    hexValue = it
                    if (it.length == 6) {
                        val hex = it.toIntOrNull(16) ?: return@OutlinedTextField
                        val color = Color(hex).copy(alpha = 1f)
                        state.setColor(color)
                    }
                }
            },
            prefix = {
                Text(
                    text = "#",
                )
            }
        )
    }

}