package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateFormat
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.preferences.Settings
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI_F = (2.0 * PI).toFloat()
private const val PHI_F = 1.618033988749895.toFloat()

// https://stackoverflow.com/a/68651222
private val Float.Companion.radiansConverter
    get() = TwoWayConverter<Float, AnimationVector2D>({ rad ->
        AnimationVector2D(sin(rad), cos(rad))
    }, {
        (atan2(it.v1, it.v2) + TWO_PI_F) % TWO_PI_F
    })

@Composable
fun OrbitClock(
    layout: Settings.ClockWidgetSettings.ClockWidgetLayout
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val millisState = remember { mutableStateOf(System.currentTimeMillis()) }
    val millis by millisState

    LaunchedEffect(null) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                millisState.value = System.currentTimeMillis()
                delay(1000)
            }
            // if (style != orbit) cancel?
        }
    }

    val verticalLayout = layout == Settings.ClockWidgetSettings.ClockWidgetLayout.Vertical
    val date = Calendar.getInstance()
    date.timeInMillis = millis
    val second = date[Calendar.SECOND]
    val minute = date[Calendar.MINUTE]
    val hour =
        if (DateFormat.is24HourFormat(LocalContext.current)) date[Calendar.HOUR_OF_DAY] else date[Calendar.HOUR]

    val animatedSecs by animateValueAsState(
        second / 60f * TWO_PI_F,
        Float.radiansConverter,
        tween(easing = EaseInOutSine)
    )
    val animatedMins by animateValueAsState(
        (minute + second / 60f) / 60f * TWO_PI_F,
        Float.radiansConverter,
        tween(easing = EaseInOutSine)
    )
    val animatedHrs by animateValueAsState(
        (hour % 12 + minute / 60f) / 12f * TWO_PI_F,
        Float.radiansConverter,
        tween(easing = EaseInOutSine)
    )

    val color = LocalContentColor.current

    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelMedium

    val strokeWidth = if (verticalLayout) 2.dp else 1.dp

    Canvas(
        modifier = Modifier
            .padding(bottom = if (verticalLayout) 8.dp else 0.dp)
            .size(if (verticalLayout) 192.dp else 56.dp)
    ) {
        val rs = size.width * 0.1f
        val rm = size.width * 0.24f
        val rh = rm + (rm - rs) * PHI_F
        if (verticalLayout) {
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = rs,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
                )
            )
        }
        drawCircle(
            color = color.copy(alpha = 0.5f),
            radius = rm,
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
        )
        drawCircle(
            color = color.copy(alpha = 0.5f),
            radius = rh,
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())),
            )
        )

        val sPos = Offset(x = sin(animatedSecs) * rs, y = -cos(animatedSecs) * rs)
        val mPos = Offset(x = sin(animatedMins) * rm, y = -cos(animatedMins) * rm)
        val hPos = Offset(x = sin(animatedHrs) * rh, y = -cos(animatedHrs) * rh)


        if (verticalLayout) {
            drawCircle(
                color = Color.Black,
                radius = size.width * 0.02f,
                center = size.center + sPos,
                blendMode = BlendMode.DstOut
            )
        }
        drawCircle(
            color = Color.Black,
            radius = size.width * 0.07f,
            center = size.center + mPos,
            blendMode = BlendMode.DstOut
        )
        drawCircle(
            color = Color.Black,
            radius = size.width * 0.09f,
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
                color = color.invert(),
                topLeft = size.center - textMResult.size.center.toOffset() + mPos,
                blendMode = BlendMode.Overlay
            )
            drawText(
                textHResult,
                color = color.invert(),
                topLeft = size.center - textHResult.size.center.toOffset() + hPos,
                blendMode = BlendMode.Overlay
            )
        }

        if (verticalLayout) {
            drawCircle(
                color = color,
                radius = size.width * 0.02f,
                center = size.center + sPos,
                blendMode = BlendMode.Overlay
            )
        }
        drawCircle(
            color = color,
            radius = size.width * 0.07f,
            center = size.center + mPos,
            blendMode = BlendMode.Overlay
        )
        drawCircle(
            color = color,
            radius = size.width * 0.09f,
            center = size.center + hPos,
            blendMode = BlendMode.Overlay
        )


    }
}

private fun Color.invert(): Color {
    val c = this.toArgb()
    val a = c shr 24
    val r = 255 - ((c shr 16) and 0xff)
    val g = 255 - (c shr 8) and 0xff
    val b = 255 - (c and 0xff)
    return Color(r, g, b, a)
}
