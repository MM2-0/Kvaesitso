package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import de.mm20.launcher2.preferences.Settings
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitClock(
    time: Long,
    layout: Settings.ClockWidgetSettings.ClockWidgetLayout
) {
    val verticalLayout = layout == Settings.ClockWidgetSettings.ClockWidgetLayout.Vertical
    val date = Calendar.getInstance()
    date.timeInMillis = time
    val minute = date[Calendar.MINUTE]
    val hour = if (DateFormat.is24HourFormat(LocalContext.current)) date[Calendar.HOUR_OF_DAY] else date[Calendar.HOUR]

    val mu by animateFloatAsState(minute / 60f * 2f * PI.toFloat())
    val heta by animateFloatAsState((hour % 12) / 12f * 2f * PI.toFloat() + (minute / 60f) * 1f / 6f * PI.toFloat())

    val color = LocalContentColor.current

    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelMedium

    val strokeWidth = if (verticalLayout) 2.dp else 1.dp

    Canvas(modifier = Modifier
        .padding(bottom = if (verticalLayout) 8.dp else 0.dp)
        .size(if (verticalLayout) 192.dp else 56.dp)
    ) {
        val rm = size.width * 0.45f
        val rh = size.width * 0.25f
        drawCircle(
            color = color.copy(alpha = 0.5f),
            radius = rm,
            style = Stroke(width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
        )
        drawCircle(
            color = color.copy(alpha = 0.5f),
            radius = rh,
            style = Stroke(width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        )

        val mPos = Offset(x = sin(mu) * rm, y = -cos(mu) * rm)
        val hPos = Offset(x = sin(heta) * rh, y = -cos(heta) * rh)

        drawCircle(
            color = Color.Black,
            radius = size.width * 0.08f,
            center = size.center + mPos,
            blendMode = BlendMode.DstOut
        )
        drawCircle(
            color = Color.Black,
            radius = size.width * 0.08f,
            center = size.center + hPos,
            blendMode = BlendMode.DstOut
        )

        if (verticalLayout) {

            val textMResult = measurer.measure(
                AnnotatedString(minute.toString()),
                maxLines = 1,
                style = textStyle
            )

            val textHResult = measurer.measure(
                AnnotatedString(hour.toString()),
                maxLines = 1,
                style = textStyle
            )

            drawText(
                textMResult,
                color = Color.Black,
                topLeft = size.center - textMResult.size.center.toOffset() + mPos
            )
            drawText(
                textHResult,
                color = Color.Black,
                topLeft = size.center - textHResult.size.center.toOffset() + hPos
            )
        }

        drawCircle(
            color = color,
            radius = size.width * 0.08f,
            center = size.center + hPos,
            blendMode = BlendMode.SrcOut
        )
        drawCircle(
            color = color,
            radius = size.width * 0.08f,
            center = size.center + mPos,
            blendMode = BlendMode.SrcOut
        )

    }
}