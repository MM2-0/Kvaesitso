package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import java.util.Calendar

@Composable
fun AnalogClock(
    time: Long,
    compact: Boolean,
    showSeconds: Boolean,
    useThemeColor: Boolean,
    darkColors: Boolean,
    style: ClockWidgetStyle.Analog,
) {
    val verticalLayout = !compact
    val date = Calendar.getInstance()
    date.timeInMillis = time
    val second = date[Calendar.SECOND]
    val minute = date[Calendar.MINUTE]
    val hour = date[Calendar.HOUR]

    val size = if (verticalLayout) 128.dp else 56.dp
    val strokeWidth = if (verticalLayout) 4.dp else 2.dp

    val color = if (useThemeColor) {
        if (!darkColors) {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.primaryContainer
        } else {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.primary
        }
    }
    else {
        LocalContentColor.current
    }

    val secondaryColor = if (useThemeColor) {
        if (LocalContentColor.current == Color.White) {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            if (LocalDarkTheme.current) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primaryContainer
        }
    }
    else {
        LocalContentColor.current.invert()
    }

    val contentColor = LocalContentColor.current

    Canvas(modifier = Modifier
        .padding(top = if (verticalLayout) 8.dp else 0.dp,
            bottom = if (verticalLayout) 8.dp else 0.dp)
        .size(size)) {
        if (style.showTicks) {
            for (hour in 0.. 11) {
                rotate(hour.toFloat() / 12f * 360f, this.size.center) {
                    drawLine(
                        secondaryColor,
                        this.size.center.copy(y = this.size.height * 0.95f),
                        this.size.center.copy(y = this.size.height),
                        strokeWidth = (strokeWidth * 0.75f).toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        rotate(hour.toFloat() / 12f * 360f + ((minute.toFloat() / 60f) * 30f) + (second.toFloat() / 120f), this.size.center) {
            drawLine(
                color,
                this.size.center, this.size.center.copy(y = this.size.height * 0.25f),
                strokeWidth = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        }
        rotate(minute.toFloat() / 60f * 360f + (second.toFloat() / 60f) * 6f, this.size.center) {
            drawLine(
                color,
                this.size.center, this.size.center.copy(y = this.size.height * 0.1f),
                strokeWidth = strokeWidth.toPx(),
                cap = StrokeCap.Round,
            )
        }
        if (verticalLayout && showSeconds) {
            rotate((second.toFloat() / 60f) * 360f, this.size.center) {
                drawLine(
                    contentColor,
                    this.size.center, this.size.center.copy(y = this.size.height * 0.05f),
                    strokeWidth = (strokeWidth / 2).toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        drawCircle(
            secondaryColor,
            radius = (strokeWidth * 1.5f).toPx(),
            center = this.size.center,
            style = Fill
        )
    }
}

private fun Color.invert(alpha: Float? = null): Color =
    Color(
        1f - red,
        1f - green,
        1f - blue,
        alpha ?: this.alpha, colorSpace
    )