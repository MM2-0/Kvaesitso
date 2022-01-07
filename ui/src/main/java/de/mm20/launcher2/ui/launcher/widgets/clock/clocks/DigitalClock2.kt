package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.mm20.launcher2.preferences.Settings


@Composable
fun DigitalClock2(
    time: Long,
    layout: Settings.ClockWidgetSettings.ClockWidgetLayout
) {
    Text(
        text = DateUtils.formatDateTime(LocalContext.current, time, DateUtils.FORMAT_SHOW_TIME),
        style = MaterialTheme.typography.displayLarge
    )
}