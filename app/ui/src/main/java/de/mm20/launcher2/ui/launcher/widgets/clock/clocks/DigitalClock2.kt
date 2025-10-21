package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.text.format.DateFormat
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.locals.LocalDarkTheme
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DigitalClock2(
    time: Long,
    compact: Boolean,
    showSeconds: Boolean,
    twentyFourHours: Boolean,
    monospaced: Boolean,
    useThemeColor: Boolean,
    darkColors: Boolean,
) {

    val verticalLayout = !compact
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

    val formatString = if (verticalLayout && showSeconds) {
        if (twentyFourHours) {
            "HH:mm:ss"
        }
        else {
            "hh:mm:ss"
        }
    } else {
        if (twentyFourHours) {
            "HH:mm"
        }
        else {
            "hh:mm"
        }
    }
    val formatter = SimpleDateFormat(formatString, Locale.getDefault())
    Text(
        modifier = Modifier.padding(top = if (verticalLayout) 12.dp else 0.dp,
            bottom = if (verticalLayout) 12.dp else 0.dp,
            start = 0.dp,
            end = 0.dp),
        text = formatter.format(time),
        style = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Normal,
            fontFeatureSettings = if (monospaced) "tnum" else null,
            color = color
        )
    )
}