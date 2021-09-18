package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.appwidget.AppWidgetHost
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.get
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.transition.OneShotLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.view.LauncherCardView
import de.mm20.launcher2.ui.legacy.widget.*
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetType
import kotlinx.android.synthetic.main.view_widget.view.*

class WidgetView : LauncherCardView {

    var onRemove: (() -> Unit)? = null


    var widget: Widget? = null

    var widgetView: LauncherWidget? = null

    var editMode = false
        set(value) {
            if (value) {
                widgetControlPanel.visibility = View.VISIBLE
                val widget = widgetWrapper[2]
                widget.visibility = View.GONE
                widgetName.visibility = View.VISIBLE
                visibility = View.VISIBLE
                layoutTransition = OneShotLayoutTransition(this)
                widgetView?.layoutTransition = null
                widgetWrapper.layoutTransition = null
            } else {
                resizeMode = false
                widgetControlPanel.visibility = View.GONE
                val widget = widgetWrapper[2] as LauncherWidget
                widget.visibility = View.VISIBLE
                widgetName.visibility = View.GONE
                visibility = if (widget.show) View.VISIBLE else View.GONE
                layoutTransition = ChangingLayoutTransition()
                widgetView?.layoutTransition = ChangingLayoutTransition()
                widgetWrapper.layoutTransition = ChangingLayoutTransition()
            }
            field = value
        }

    private var resizeMode = false
        set(value) {
            if (value == field) return
            onResizeModeChange?.invoke(value)
            if (value) {
                widgetResizeDragHandle.visibility = View.VISIBLE
                val widget = widgetWrapper[2]
                widget.visibility = View.VISIBLE
                widgetName.visibility = View.GONE
            } else {
                widgetResizeDragHandle.visibility = View.GONE
                if (editMode) {
                    val widget = widgetWrapper[2]
                    widget.visibility = View.GONE
                    widgetName.visibility = View.VISIBLE
                }

            }
            layoutTransition = OneShotLayoutTransition(this)
            field = value
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    init {
        View.inflate(context, R.layout.view_widget, this)


        widgetActionResize.setOnClickListener {
            resizeMode = !resizeMode
        }
        widgetActionRemove.setOnClickListener {
            onRemove?.invoke()
        }
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }

        elevation = if (backgroundOpacity < 255) 0f else resources.getDimension(R.dimen.card_elevation)

        TooltipCompat.setTooltipText(widgetActionResize, context.getString(R.string.widget_action_adjust_height))
        TooltipCompat.setTooltipText(widgetActionRemove, context.getString(R.string.widget_action_remove))
        TooltipCompat.setTooltipText(widgetActionSettings, context.getString(R.string.widget_action_settings))
    }

    var onResizeModeChange: ((Boolean) -> Unit)? = null

    fun setWidget(widget: Widget, widgetHost: AppWidgetHost): Boolean {
        if (widget.type == WidgetType.INTERNAL) {
            widgetView = when (widget.data) {
                CalendarWidget.ID -> CalendarWidget(context)
                WeatherWidget.ID -> WeatherWidget(context)
                MusicWidget.ID -> MusicWidget(context)
                else -> return false
            }
            widgetActionResize.visibility = View.GONE
            widgetActionSettings.visibility = if (widgetView?.hasSettings == true) View.VISIBLE else View.GONE
            widgetResizeDragHandle.resizeView = widgetView
            widgetWrapper.addView(widgetView, 2)
            widgetName.text = widgetView?.name
            visibility = if (widgetView?.show == true) View.VISIBLE else View.GONE
            widgetActionSettings.setOnClickListener {
                widgetView?.openSettings()
                /*(context as? Activity)?.finish()
                context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                    putExtra(SettingsActivity.FRAGMENT, widgetView?.settingsFragment)
                })*/
            }
        } else {
            widgetView = ExternalWidget(context, widget, widgetHost)
            widgetResizeDragHandle.resizeView = widgetView
            widgetResizeDragHandle.onResize = {
                widget.height = (it / dp).toInt()
            }
            widgetWrapper.addView(widgetView, 2)
            widgetName.text = widgetView?.name
            widgetActionResize.visibility = View.VISIBLE
            widgetActionSettings.visibility = View.GONE
            visibility = if (widgetView?.show == true) View.VISIBLE else View.GONE
        }
        widgetView?.onVisibilityChanged = {
            visibility = if (it) View.VISIBLE else View.GONE
        }
        widgetView?.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }
        this.widget = widget
        return true
    }

    fun getDragHandle(): View {
        return widgetDragHandle
    }

    fun update() {
        val widget = widgetWrapper[2] as? LauncherWidget ?: return
        widget.update()
        visibility = if (widget.show) View.VISIBLE else View.GONE
    }

}