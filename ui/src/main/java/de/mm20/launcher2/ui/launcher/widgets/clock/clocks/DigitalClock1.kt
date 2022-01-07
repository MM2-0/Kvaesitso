package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateFormat
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.preferences.Settings
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DigitalClock1(
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
    Text(
        modifier = Modifier
            .offset(y = if (verticalLayout) 16.dp else 0.dp),
        text = format.format(time),
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = if (verticalLayout) 100.sp else 48.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 0.8.em,
            letterSpacing = -0.1.em,
        )
    )
}