package de.mm20.launcher2.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import de.mm20.launcher2.ui.ktx.toHexString

@Composable
fun ColorPicker(
    value: Color,
    onValueChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val hsl = remember { floatArrayOf(0f, 0f, 0f) }

    ColorUtils.colorToHSL(value.toArgb(), hsl)

    val hue = hsl[0]
    val sat = hsl[1]
    val lig = hsl[2]

    var hex by remember { mutableStateOf(value.toHexString()) }

    LaunchedEffect(value) {
        hex = value.toHexString()
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .background(value)
        )
        SliderRow("H") {
            Slider(
                value = hue,
                valueRange = 0f..360f,
                onValueChange = {
                    val newValue = Color(ColorUtils.HSLToColor(floatArrayOf(it, sat, lig)))
                    onValueChanged(newValue)
                }
            )
        }
        SliderRow("S") {
            Slider(
                value = sat,
                valueRange = 0f..1f,
                onValueChange = {
                    val newValue = Color(ColorUtils.HSLToColor(floatArrayOf(hue, it, lig)))
                    onValueChanged(newValue)
                }
            )
        }
        SliderRow("L") {
            Slider(
                value = lig,
                valueRange = 0f..1f,
                onValueChange = {
                    val newValue = Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, it)))
                    onValueChanged(newValue)
                }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                "Hex: ",
                modifier = Modifier.weight(2f),
                style = MaterialTheme.typography.subtitle2
            )
            OutlinedTextField(
                value = hex,
                onValueChange = {
                    if (it.matches(Regex("^#([a-fA-F0-9]{6})$"))) {
                        val colorInt = it.substring(1).toInt(16)
                        onValueChanged(Color(colorInt).copy(alpha = 1f))
                    } else {
                        hex = it
                    }
                },
                modifier = Modifier.weight(6f)
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.subtitle2
        )
        Box(
            modifier = Modifier.weight(7f)
        ) {
            content()
        }
    }
}