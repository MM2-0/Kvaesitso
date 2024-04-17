package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.ui.launcher.widgets.external.AppWidgetHost

@Composable
fun CustomClock(
    style: ClockWidgetStyle.Custom,
    compact: Boolean,
    useThemeColor: Boolean,
    darkColors: Boolean,
) {
    val widgetId = style.widgetId

    if (widgetId == null) {
        Text("Hmmm…")
    } else {
        val context = LocalContext.current
        val widgetInfo = remember(widgetId) {
            AppWidgetManager.getInstance(context)
                .getAppWidgetInfo(widgetId)
        }
        if (widgetInfo != null) {
            AppWidgetHost(
                widgetInfo = widgetInfo,
                widgetId = widgetId,
                useThemeColors = useThemeColor,
                onLightBackground = darkColors,
                borderless = compact,
                modifier = Modifier.widthIn(max = 250.dp).height(if (compact) 64.dp else 200.dp)
            )
        }
    }
}