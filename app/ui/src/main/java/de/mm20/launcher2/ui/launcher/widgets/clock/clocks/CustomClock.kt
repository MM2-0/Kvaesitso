package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.ClockWidgetStyle
import de.mm20.launcher2.ui.launcher.sheets.WidgetPickerSheet
import de.mm20.launcher2.ui.launcher.widgets.external.ExternalWidget

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
            ExternalWidget(
                widgetInfo = widgetInfo,
                widgetId = widgetId,
                height = if (compact) 64 else 200,
                useThemeColors = useThemeColor,
                onLightBackground = darkColors,
                borderless = compact,
                modifier = Modifier.widthIn(max = 250.dp)
            )
        }
    }
}