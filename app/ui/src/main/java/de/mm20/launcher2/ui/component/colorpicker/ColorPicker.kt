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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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


@Composable
fun ColorPicker(
    value: Color,
    onValueChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (hue, sat, vl) = remember(value) {
        val hsv = FloatArray(3)
        val r = value.red * 255f
        val g = value.green * 255f
        val b = value.blue * 255f
        AndroidColor.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)
        hsv
    }
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
                                onValueChanged(Color.hsv(h.toFloat(), sat, vl))
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
                            onValueChanged(Color.hsv(h.toFloat(), sat, vl))

                        }
                    }
                    .padding(8.dp)
            ) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red
                        )
                    ),
                    style = Stroke(20.dp.toPx())
                )
                drawCircle(
                    color = value,
                    style = Fill,
                    center = center,
                    radius = size.minDimension / 2 - 18.dp.toPx()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(hue),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .shadow(1.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .align(AbsoluteAlignment.CenterRight)
                )
            }
        }
        Slider(
            modifier = Modifier.padding(top = 16.dp),
            value = sat,
            onValueChange = {
                onValueChanged(Color.hsv(hue, it, vl))
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
                                Color.hsv(hue, 0f, 1f),
                                Color.hsv(hue, 1f, 1f)
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
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        )
        Slider(
            modifier = Modifier,
            value = vl,
            onValueChange = {
                onValueChanged(Color.hsv(hue, sat, it))
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
                                Color.hsv(hue, sat, 0f),
                                Color.hsv(hue, sat, 1f)
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
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        )

        var hexValue by remember(value) {
            mutableStateOf(
                value.toHexString().substring(1)
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
                        onValueChanged(color)
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