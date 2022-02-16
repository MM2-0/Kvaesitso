package de.mm20.launcher2.ui.legacy.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.get
import androidx.core.view.iterator
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.widgets.Widget

@SuppressLint("ViewConstructor")
class ExternalWidget(
        context: Context,
        val widget: Widget,
        host: AppWidgetHost
) : LauncherWidget(context) {

    val widgetInfo: AppWidgetProviderInfo?

    val widgetView: View

    init {
        val id = widget.data.toInt()
        widgetInfo = AppWidgetManager.getInstance(context.applicationContext).getAppWidgetInfo(id)
        widgetView = host.createView(context.applicationContext, id, widgetInfo)
                ?: View(context)
        if (widgetView is AppWidgetHostView && widgetView.childCount > 0) {
            enableNestedScroll(widgetView[0])
        }
        val h = widget.height * dp
        val params = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, h.toInt())
        val p = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        layoutParams = params
        widgetView.layoutParams = p
        addView(widgetView)
    }

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        super.setLayoutParams(params)
        params ?: return
        doOnNextLayout {
            val width = if (params.width > 0) params.width else it.width
            val height = if (params.height > 0) params.height else widgetInfo?.minHeight ?: it.height
            if (widgetView is AppWidgetHostView) {
                widgetView.updateAppWidgetSize(Bundle(), 0, 0, width, height)
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

    override val canResize: Boolean
        get() = true
    override val name: String
        get() = widgetInfo?.loadLabel(context.packageManager) ?: ""

}