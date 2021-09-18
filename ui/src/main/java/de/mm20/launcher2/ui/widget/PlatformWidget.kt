package de.mm20.launcher2.ui.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.mm20.launcher2.ui.locals.LocalAppWidgetHost
import de.mm20.launcher2.ui.toPixels
import de.mm20.launcher2.widgets.Widget

@Composable
fun PlatformWidget(widget: Widget) {
    val context = LocalContext.current.applicationContext

    val widgetId = widget.data.toInt()

    val appWidgetHost = LocalAppWidgetHost.current
    val widgetInfo = remember {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
    }

    val height = widget.height.dp.toPixels().toInt()
    val isLightTheme = MaterialTheme.colors.isLight

    AndroidView(
        factory = {
            val view = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height)
            }
            val widgetView = appWidgetHost!!.createView(context, widgetId, widgetInfo).apply {
                layoutParams = ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            view.addView(widgetView)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                widgetView.setOnLightBackground(isLightTheme)
            }
            return@AndroidView view
        }
    )
}