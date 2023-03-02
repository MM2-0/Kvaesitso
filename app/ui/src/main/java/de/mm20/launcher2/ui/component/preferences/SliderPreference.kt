package de.mm20.launcher2.ui.component.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.log
import kotlin.math.roundToInt

@Composable
fun SliderPreference(
    title: String,
    icon: ImageVector? = null,
    value: Float,
    min: Float = 0f,
    max: Float = 1f,
    step: Float? = null,
    onValueChanged: (Float) -> Unit,
    enabled: Boolean = true,
    label: (@Composable (Float) -> Unit)? = null
) {
    var sliderValue by remember(value) { mutableStateOf(value) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .padding(start = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    modifier = Modifier.weight(1f),
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                    },
                    valueRange = min..max,
                    steps = step?.let { ((max - min) / it).toInt() - 1 } ?: 0,
                    onValueChangeFinished = {
                        onValueChanged(sliderValue)
                    }
                )
                if (label != null) {
                    label(sliderValue)
                } else {
                    val decimalPlaces = -log(step ?: 0.01f, 10f)
                    val format = remember { DecimalFormat().apply {
                        maximumFractionDigits = floor(decimalPlaces).toInt()
                        minimumFractionDigits = 0
                    } }
                    Text(
                        modifier = Modifier.width(56.dp).padding(start = 24.dp),
                        text = format.format(sliderValue),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
fun SliderPreference(
    title: String,
    icon: ImageVector? = null,
    value: Int,
    min: Int = 0,
    max: Int = 100,
    step: Int = 1,
    onValueChanged: (Int) -> Unit,
    enabled: Boolean = true,
    label: (@Composable (Int) -> Unit)? = null
) {
    SliderPreference(
        title = title,
        icon = icon,
        value = value.toFloat(),
        enabled = enabled,
        min = min.toFloat(),
        max = max.toFloat(),
        step = step.toFloat(),
        onValueChanged = {
            onValueChanged(it.roundToInt())
        },
        label = if (label == null) null else {
            { label(it.roundToInt()) }
        }
    )
}

@Composable
inline fun <reified T: Enum<T>> SliderPreference(
    title: String,
    icon: ImageVector? = null,
    value: T,
    enabled: Boolean = true,
    labels: List<EnumLocalization<T>>? = null,
    crossinline onValueChanged: (T) -> Unit
) {
    val values = enumValues<T>()
    SliderPreference(
        title = title,
        icon = icon,
        value = values.indexOf(value),
        min = 0,
        max = values.size - 1,
        step = 1,
        onValueChanged = {
            onValueChanged(values[it])
        },
        enabled = enabled,
        label = if (labels == null) null else {
            {
                val idx = labels.indexOfFirst { l -> l.value == values[it] }
                Text(
                    modifier = Modifier.width(56.dp).padding(start = 24.dp),
                    text = if (idx != -1) labels[idx].label else "",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    )
}

typealias EnumLocalization<T> = ListPreferenceItem<T>
