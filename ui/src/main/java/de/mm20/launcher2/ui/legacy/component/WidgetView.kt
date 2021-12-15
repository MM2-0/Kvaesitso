package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.appwidget.AppWidgetHost
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.get
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.transition.OneShotLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewWidgetBinding
import de.mm20.launcher2.ui.legacy.view.LauncherCardView
import de.mm20.launcher2.ui.legacy.widget.*
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetType

class WidgetView : LauncherCardView {

    var onRemove: (() -> Unit)? = null


    var widget: Widget? = null

    var widgetView: LauncherWidget? = null

    var editMode = false
        set(value) {
            if (value) {
                binding.widgetControlPanel.visibility = View.VISIBLE
                val widget = binding.widgetWrapper[2]
                widget.visibility = View.GONE
                binding.widgetName.visibility = View.VISIBLE
                visibility = View.VISIBLE
                layoutTransition = OneShotLayoutTransition(this)
                widgetView?.layoutTransition = null
                binding.widgetWrapper.layoutTransition = null
            } else {
                resizeMode = false
                binding.widgetControlPanel.visibility = View.GONE
                val widget = binding.widgetWrapper[2] as LauncherWidget
                widget.visibility = View.VISIBLE
                binding.widgetName.visibility = View.GONE
                visibility = if (widget.show) View.VISIBLE else View.GONE
                layoutTransition = ChangingLayoutTransition()
                widgetView?.layoutTransition = ChangingLayoutTransition()
                binding.widgetWrapper.layoutTransition = ChangingLayoutTransition()
            }
            field = value
        }

    private var resizeMode = false
        set(value) {
            if (value == field) return
            onResizeModeChange?.invoke(value)
            if (value) {
                binding.widgetResizeDragHandle.visibility = View.VISIBLE
                val widget = binding.widgetWrapper[2]
                widget.visibility = View.VISIBLE
                binding.widgetName.visibility = View.GONE
            } else {
                binding.widgetResizeDragHandle.visibility = View.GONE
                if (editMode) {
                    val widget = binding.widgetWrapper[2]
                    widget.visibility = View.GONE
                    binding.widgetName.visibility = View.VISIBLE
                }

            }
            layoutTransition = OneShotLayoutTransition(this)
            field = value
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val binding = ViewWidgetBinding.inflate(LayoutInflater.from(context), this)

    init {
        binding.widgetActionResize.setOnClickListener {
            resizeMode = !resizeMode
        }
        binding.widgetActionRemove.setOnClickListener {
            onRemove?.invoke()
        }
        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }

        elevation = if (backgroundOpacity < 255) 0f else resources.getDimension(R.dimen.card_elevation)

        TooltipCompat.setTooltipText(binding.widgetActionResize, context.getString(R.string.widget_action_adjust_height))
        TooltipCompat.setTooltipText(binding.widgetActionRemove, context.getString(R.string.widget_action_remove))
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
            binding.widgetActionResize.visibility = View.GONE
            binding.widgetResizeDragHandle.resizeView = widgetView
            binding.widgetWrapper.addView(widgetView, 2)
            binding.widgetName.text = widgetView?.name
            visibility = if (widgetView?.show == true) View.VISIBLE else View.GONE
        } else {
            widgetView = ExternalWidget(context, widget, widgetHost)
            binding.widgetResizeDragHandle.resizeView = widgetView
            binding.widgetResizeDragHandle.onResize = {
                widget.height = (it / dp).toInt()
            }
            binding.widgetWrapper.addView(widgetView, 2)
            binding.widgetName.text = widgetView?.name
            binding.widgetActionResize.visibility = View.VISIBLE
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
        return binding.widgetDragHandle
    }

}