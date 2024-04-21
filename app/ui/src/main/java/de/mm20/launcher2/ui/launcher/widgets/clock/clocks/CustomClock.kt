package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

    if (widgetId != null) {
        val context = LocalContext.current
        val widgetInfo = remember(widgetId) {
            AppWidgetManager.getInstance(context)
                .getAppWidgetInfo(widgetId)
        }
        if (widgetInfo != null) {
            val width = style.width
            val height = style.height
            AppWidgetHost(
                widgetInfo = widgetInfo,
                widgetId = widgetId,
                useThemeColors = useThemeColor,
                onLightBackground = darkColors,
                borderless = compact,
                modifier = Modifier
                    .then(
                        when {
                            compact && width == null -> Modifier.widthIn(max = 200.dp)
                            compact && width != null -> Modifier.width(width.coerceAtMost(200).dp)
                            !compact && width != null -> Modifier.width(width.dp)
                            else -> Modifier.fillMaxWidth()
                        }
                    )
                    .then(
                        when {
                            compact -> Modifier.height(height.coerceAtMost(64).dp)
                            else -> Modifier.height(height.dp)
                        }
                    )
            )
        }
    }
}