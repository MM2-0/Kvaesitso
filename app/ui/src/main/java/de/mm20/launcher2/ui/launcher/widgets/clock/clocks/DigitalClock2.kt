package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight


@Composable
fun DigitalClock2(
    time: Long,
    compact: Boolean,
) {
    Text(
        text = DateUtils.formatDateTime(LocalContext.current, time, DateUtils.FORMAT_SHOW_TIME),
        style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Normal
        )
    )
}