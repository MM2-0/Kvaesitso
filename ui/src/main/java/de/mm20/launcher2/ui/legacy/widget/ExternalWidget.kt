package de.mm20.launcher2.ui.legacy.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.ScrollView
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

    init {
        val id = widget.data.toInt()
        widgetInfo = AppWidgetManager.getInstance(context.applicationContext).getAppWidgetInfo(id)
        show = widgetInfo != null
        val widgetView = host.createView(context.applicationContext, id, widgetInfo)
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

    private fun enableNestedScroll(view: View) {
        if (view is ViewGroup) {
            for (child in view.iterator()) {
                enableNestedScroll(child)
            }
        }
        if (view is ListView || view is ScrollView) view.isNestedScrollingEnabled = true
    }

    override fun update() {}

    override val compactViewRanking: Int
        get() = -1
    override val compactView: CompactView?
        get() = null
    override val settingsFragment: String?
        get() = null
    override val canResize: Boolean
        get() = true
    override val name: String
        get() = widgetInfo?.loadLabel(context.packageManager) ?: ""

}