package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateFormat
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.ui.ktx.toPixels
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DigitalClock1(
    time: Long,
    compact: Boolean,
    style: ClockWidgetStyle.Digital1 = ClockWidgetStyle.Digital1(),
) {
    val verticalLayout = !compact
    val format = SimpleDateFormat(
        if (verticalLayout) {
            if (DateFormat.is24HourFormat(LocalContext.current)) "HH\nmm" else "hh\nmm"
        } else {
            if (DateFormat.is24HourFormat(LocalContext.current)) "HH mm" else "hh mm"
        },
        Locale.getDefault()
    )

    val formattedString = format.format(time)

    val textStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = if (verticalLayout) 100.sp else 48.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
        lineHeight = 0.8.em,
        drawStyle = if (style.outlined) Stroke(width = 2.dp.toPixels()) else Fill,
        color = if (style.variant == ClockWidgetStyle.Digital1.Variant.MDY) {
            if (LocalContentColor.current == Color.White) {
                if (LocalDarkTheme.current) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            } else {
                if (LocalDarkTheme.current) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.primary
            }
        } else LocalContentColor.current
    )

    val modifier = Modifier.offset(0.dp, if (verticalLayout) 16.dp else 0.dp)

    if (style.variant == ClockWidgetStyle.Digital1.Variant.OnePlus) {
        val hour = formattedString.substring(0, 2)
        Text(
            modifier = modifier,
            text = buildAnnotatedString {
                hour.forEach {
                    if (it == '1') {
                        withStyle(style = SpanStyle(color = Color(0xFFC41442))) {
                            append(it)
                        }
                    } else {
                        append(it)
                    }
                }
                append(formattedString.substring(2))
            },
            style = textStyle
        )
        return
    }
    Text(
        modifier = modifier,
        text = formattedString,
        style = textStyle,
    )
}