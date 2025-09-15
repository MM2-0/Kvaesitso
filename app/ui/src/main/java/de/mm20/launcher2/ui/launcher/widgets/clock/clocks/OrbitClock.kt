package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import de.mm20.launcher2.ktx.TWO_PI
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import palettes.TonalPalette
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

private const val PHI_F = 1.618033988749895.toFloat()

@Composable
fun OrbitClock(
    time: Long,
    compact: Boolean,
    showSeconds: Boolean,
    twentyFourHours: Boolean,
    monospaced: Boolean,
    useThemeColor: Boolean,
    darkColors: Boolean,
) {
    val verticalLayout = !compact

    val parsed = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())

    val second = parsed.second
    val minute = parsed.minute
    val hour = parsed.hour
    val formattedHour = (
            if (twentyFourHours)
                hour
            else {
                ((hour + 11) % 12) + 1
            }).toString()

    val secsAngleStart = second / 60f * Float.TWO_PI
    val minsAngleStart = minute / 60f * Float.TWO_PI + secsAngleStart / 60f
    val hourAngleStart = hour % 12 / 12f * Float.TWO_PI + minsAngleStart / 12f

    val infiniteTransition = rememberInfiniteTransition(label = "timeInfiniteTransition")

    val animatedSecs by infiniteTransition.animateFloat(
        initialValue = secsAngleStart,
        targetValue = secsAngleStart + Float.TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 60 * 1000,
                easing = LinearEasing
            )
        ),
        label = "secondsAnimation"
    )
    val animatedMins by infiniteTransition.animateFloat(
        initialValue = minsAngleStart,
        targetValue = minsAngleStart + Float.TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 60 * 60 * 1000,
                easing = LinearEasing
            )
        ),
        label = "minutesAnimation"
    )
    val animatedHrs by infiniteTransition.animateFloat(
        initialValue = hourAngleStart,
        targetValue = hourAngleStart + Float.TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 12 * 60 * 60 * 1000,
                easing = LinearEasing
            )
        ),
        label = "hoursAnimation"
    )

    val fgTone = if (!darkColors) 10 else 90
    val bgTone = if (!darkColors) 90 else 30

    val background = if (useThemeColor) {
        Color(TonalPalette.fromInt(MaterialTheme.colorScheme.primaryContainer.toArgb()).tone(bgTone))
    }
    else {
        LocalContentColor.current
    }

    val foreground = if (useThemeColor) {
        Color(TonalPalette.fromInt(MaterialTheme.colorScheme.onPrimaryContainer.toArgb()).tone(fgTone))
    }
    else {
        LocalContentColor.current.invert()
    }

    val contentColor = LocalContentColor.current

    val textMeasurer = rememberTextMeasurer()
    val minuteStyle = MaterialTheme.typography.labelMedium.copy(
        fontFeatureSettings = if (monospaced) "tnum" else null
    )
    val hourStyle = MaterialTheme.typography.labelLarge.copy(
        fontFeatureSettings = if (monospaced) "tnum" else null
    )

    val strokeWidth = if (verticalLayout) 2.dp else 1.dp

    Canvas(
        modifier = Modifier
            .padding(bottom = if (verticalLayout) 8.dp else 0.dp,
                top = if (verticalLayout) 8.dp else 0.dp)
            .size(if (verticalLayout) 192.dp else 56.dp)
    ) {

        val rs = size.width * 0.08f
        val rm = size.width * 0.22f
        val rh = rm + (rm - rs) * PHI_F

        val sSize = size.width * 0.0175f
        val mSize = size.width * 0.08f
        val hSize = rh + sSize + rs - 2f * rm

        if (verticalLayout && showSeconds) {
            drawCircle(
                color = contentColor.copy(alpha = 0.5f),
                radius = rs,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
                )
            )
        }
        drawCircle(
            color = contentColor.copy(alpha = 0.5f),
            radius = rm,
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(
                        (2 * PHI_F).dp.toPx(),
                        (2 * PHI_F).dp.toPx()
                    )
                )
            )
        )
        drawCircle(
            color = contentColor.copy(alpha = 0.5f),
            radius = rh,
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(
                        (2 * PHI_F * PHI_F).dp.toPx(),
                        (2 * PHI_F * PHI_F).dp.toPx()
                    )
                ),
            )
        )

        val sPos = Offset(x = sin(animatedSecs) * rs, y = -cos(animatedSecs) * rs)
        val mPos = Offset(x = sin(animatedMins) * rm, y = -cos(animatedMins) * rm)
        val hPos = Offset(x = sin(animatedHrs) * rh, y = -cos(animatedHrs) * rh)

        if (verticalLayout && showSeconds) {
            drawCircle(
                color = Color.Black,
                radius = sSize,
                center = size.center + sPos,
                blendMode = BlendMode.DstOut
            )
        }
        drawCircle(
            color = Color.Black,
            radius = mSize,
            center = size.center + mPos,
            blendMode = BlendMode.DstOut
        )
        drawCircle(
            color = Color.Black,
            radius = hSize,
            center = size.center + hPos,
            blendMode = BlendMode.DstOut
        )

        if (verticalLayout) {

            val textMResult = textMeasurer.measure(
                AnnotatedString(minute.toString()),
                maxLines = 1,
                style = minuteStyle
            )

            val textHResult = textMeasurer.measure(
                AnnotatedString(formattedHour),
                maxLines = 1,
                style = hourStyle
            )

            drawText(
                textMResult,
                color = Color.Black,
                topLeft = size.center - textMResult.size.center.toOffset() + mPos,
                blendMode = BlendMode.DstOut
            )
            drawText(
                textHResult,
                color = Color.Black,
                topLeft = size.center - textHResult.size.center.toOffset() + mPos,
                blendMode = BlendMode.DstOut
            )

            drawText(
                textMResult,
                color = foreground,
                topLeft = size.center - textMResult.size.center.toOffset() + mPos,
                blendMode = BlendMode.Overlay
            )
            drawText(
                textHResult,
                color = foreground,
                topLeft = size.center - textHResult.size.center.toOffset() + hPos,
                blendMode = BlendMode.Overlay
            )
        }

        if (verticalLayout && showSeconds) {
            drawCircle(
                color = background,
                radius = sSize,
                center = size.center + sPos,
                blendMode = BlendMode.Overlay
            )
        }
        drawCircle(
            color = background,
            radius = mSize,
            center = size.center + mPos,
            blendMode = BlendMode.Overlay
        )
        drawCircle(
            color = background,
            radius = hSize,
            center = size.center + hPos,
            blendMode = BlendMode.Overlay
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

