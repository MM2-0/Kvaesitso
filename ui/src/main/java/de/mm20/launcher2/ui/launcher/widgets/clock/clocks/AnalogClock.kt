package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.Settings
import java.util.*

@Composable
fun AnalogClock(
    time: Long,
    layout: Settings.ClockWidgetSettings.ClockWidgetLayout
) {
    val verticalLayout = layout == Settings.ClockWidgetSettings.ClockWidgetLayout.Vertical
    val date = Calendar.getInstance()
    date.timeInMillis = time
    val minute = date[Calendar.MINUTE]
    val hour = date[Calendar.HOUR]

    val size = if (verticalLayout) 128.dp else 56.dp
    val strokeWidth = if (verticalLayout) 4.dp else 2.dp

    val color = LocalContentColor.current
    Canvas(modifier = Modifier
        .padding(bottom = if (verticalLayout) 8.dp else 0.dp)
        .size(size)) {
        drawCircle(
            color,
            radius = strokeWidth.toPx(),
            center = this.size.center,
            style = Fill
        )
        rotate(hour.toFloat() / 12f * 360f + minute.toFloat() / 60f * 5f, this.size.center) {
            drawLine(
                color,
                this.size.center, this.size.center.copy(y = this.size.height * 0.25f),
                strokeWidth = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        }
        rotate(minute.toFloat() / 60 * 360, this.size.center) {
            drawLine(
                color,
                this.size.center, this.size.center.copy(y = this.size.height * 0.1f),
                strokeWidth = strokeWidth.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}