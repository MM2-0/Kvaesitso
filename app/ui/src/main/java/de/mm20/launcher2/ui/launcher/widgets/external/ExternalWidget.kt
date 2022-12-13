package de.mm20.launcher2.ui.launcher.widgets.external

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnNextLayout
import androidx.core.view.iterator
import de.mm20.launcher2.ui.ktx.toPixels
import kotlin.math.roundToInt

@Composable
fun ExternalWidget(
    appWidgetHost: AppWidgetHost,
    widgetId: Int,
    height: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val widgetInfo = remember(widgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
    }
    BoxWithConstraints {
        val maxWidth = maxWidth
        key(widgetId) {
            AndroidView(
                modifier = modifier
                    .fillMaxWidth()
                    .height(height.dp),
                factory = {
                    val view = appWidgetHost.createView(it.applicationContext, widgetId, widgetInfo)
                    enableNestedScroll(view)
                    return@AndroidView view
                },
                update = {
                    it.updateAppWidgetSize(
                        null,
                        maxWidth.value.roundToInt(),
                        height,
                        maxWidth.value.roundToInt(),
                        height,
                    )
                }
            )
        }
    }
}

private fun enableNestedScroll(view: View) {
    if (view is ViewGroup) {
        for (child in view.iterator()) {
            enableNestedScroll(child)
        }
    }
    if (view is ListView || view is ScrollView) view.isNestedScrollingEnabled = true
}