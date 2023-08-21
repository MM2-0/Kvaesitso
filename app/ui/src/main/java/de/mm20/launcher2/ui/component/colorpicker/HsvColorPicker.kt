package de.mm20.launcher2.ui.component.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.ktx.toHexString
import kotlin.math.atan2
import android.graphics.Color as AndroidColor

@Stable
class HsvColorPickerState(
    initialColor: Color,
    val onColorChanged: (Color) -> Unit,
) {
    var hue by mutableStateOf(0f)
    var sat by mutableStateOf(0f)
    var value by mutableStateOf(0f)

    val color by derivedStateOf {
        Color.hsv(hue, sat, value)
    }

    internal fun setHue(hue: Float) {
        this.hue = hue
        onColorChanged(Color.hsv(hue, sat, value))
    }

    internal fun setSat(sat: Float) {
        this.sat = sat
        onColorChanged(Color.hsv(hue, sat, value))
    }

    internal fun setValue(value: Float) {
        this.value = value
        onColorChanged(Color.hsv(hue, sat, value))
    }

    internal fun setColor(color: Color) {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(
            (color.red * 255f).toInt(),
            (color.green * 255f).toInt(),
            (color.blue * 255f).toInt(),
            hsv
        )
        this.hue = hsv[0]
        this.sat = hsv[1]
        this.value = hsv[2]
        onColorChanged(color)
    }

    init {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(
            (initialColor.red * 255f).toInt(),
            (initialColor.green * 255f).toInt(),
            (initialColor.blue * 255f).toInt(),
            hsv
        )
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }
}

@Composable
fun rememberHsvColorPickerState(initialColor: Color, onColorChanged: (Color) -> Unit): HsvColorPickerState {
    return remember(initialColor, onColorChanged) {
        HsvColorPickerState(initialColor, onColorChanged)
    }
}

@Composable
fun HsvColorPicker(
    state: HsvColorPickerState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
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
                            Color.hsv(0f, 1f, 1f),
                            Color.hsv(60f, 1f, 1f),
                            Color.hsv(120f, 1f, 1f),
                            Color.hsv(180f, 1f, 1f),
                            Color.hsv(240f, 1f, 1f),
                            Color.hsv(300f, 1f, 1f),
                            Color.hsv(360f, 1f, 1f),
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
        Slider(
            modifier = Modifier.padding(top = 16.dp),
            value = state.sat,
            onValueChange = {
                state.setSat(it)
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
                                Color.hsv(state.hue, 0f, 1f),
                                Color.hsv(state.hue, 1f, 1f)
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
        Slider(
            modifier = Modifier,
            value = state.value,
            onValueChange = {
                state.setValue(it)
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
                                Color.hsv(state.hue, state.sat, 0f),
                                Color.hsv(state.hue, state.sat, 1f)
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

        var hexValue by remember(state.color) {
            mutableStateOf(
                state.color.toHexString().substring(1)
            )
        }

        OutlinedTextField(
            modifier = Modifier.padding(top = 16.dp).padding(horizontal = 16.dp),
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