package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateFormat
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.preferences.Settings
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OneClock(
    time: Long,
    layout: Settings.ClockWidgetSettings.ClockWidgetLayout
) {
    val verticalLayout = layout == Settings.ClockWidgetSettings.ClockWidgetLayout.Vertical
    val format = SimpleDateFormat(
        if (verticalLayout) {
            if (DateFormat.is24HourFormat(LocalContext.current)) "HH\nmm" else "hh\nmm"
        } else {
            if (DateFormat.is24HourFormat(LocalContext.current)) "HH mm" else "hh mm"
        },
        Locale.getDefault()
    )
    val formattedString = format.format(time)

    val hour = formattedString.substring(0, 2)

    val annotatedString = buildAnnotatedString {
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
    }


    Text(
        modifier = Modifier.offset(0.dp, if (verticalLayout) 16.dp else 0.dp),
        text = annotatedString,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = if (verticalLayout) 100.sp else 48.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 0.8.em,
        )
    )
}