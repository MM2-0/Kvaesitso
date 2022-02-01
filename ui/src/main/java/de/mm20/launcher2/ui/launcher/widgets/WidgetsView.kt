package de.mm20.launcher2.ui.launcher.widgets

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.iterator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.balsikandar.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.transition.OneShotLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewWidgetsBinding
import de.mm20.launcher2.ui.legacy.component.WidgetView
import de.mm20.launcher2.widgets.Widget
import de.mm20.launcher2.widgets.WidgetType
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

class WidgetsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding = ViewWidgetsBinding.inflate(LayoutInflater.from(context), this)

    private val widgetHost: AppWidgetHost = AppWidgetHost(context.applicationContext, 44203)

    private val viewModel: WidgetsVM by (context as AppCompatActivity).viewModels()

    private lateinit var widgets: MutableList<Widget>

    private val pickWidgetLauncher: ActivityResultLauncher<Intent>
    private val configureWidgetLauncher: ActivityResultLauncher<Intent>

    init {
        context as AppCompatActivity

        layoutTransition = ChangingLayoutTransition()
        binding.widgetList.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        configureWidgetLauncher = context.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val data = it.data ?: return@registerForActivityResult
            if (it.resultCode == Activity.RESULT_OK) {
                bindAppWidget(data)
            }
        }

        pickWidgetLauncher = context.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val data = it.data ?: return@registerForActivityResult
            val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (widgetId == -1) return@registerForActivityResult
            if (it.resultCode == Activity.RESULT_OK) {
                val appWidget = AppWidgetManager.getInstance(context)
                    .getAppWidgetInfo(widgetId) ?: return@registerForActivityResult
                if (appWidget.configure != null) {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    intent.component = appWidget.configure
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    configureWidgetLauncher.launch(intent)
                } else {
                    bindAppWidget(data)
                }
            } else {
                widgetHost.deleteAppWidgetId(widgetId)
            }
        }

        viewModel.widgets.observe(context) {
            if (it != null && !::widgets.isInitialized) {
                widgets = it.toMutableList()
                initWidgets()
            }
        }

        viewModel.isEditMode.observe(context) {
            if (it) {
                binding.clockWidget.visibility = View.GONE

                for (v in binding.widgetList.iterator()) {
                    if (v is WidgetView) {
                        v.editMode = true
                        v.onResizeModeChange = {
                            OneShotLayoutTransition.run(binding.widgetList)
                            OneShotLayoutTransition.run(this)
                        }
                    }
                }
                OneShotLayoutTransition.run(binding.widgetList)
                OneShotLayoutTransition.run(this)
                binding.fabEditWidget.apply {
                    setIconResource(R.drawable.ic_add)
                    setText(R.string.widget_add_widget)
                    setOnClickListener {
                        addWidget()
                    }
                }
            } else {
                if (::widgets.isInitialized) viewModel.saveWidgets(widgets)
                binding.widgetList.layoutTransition = ChangingLayoutTransition()
                binding.clockWidget.visibility = View.VISIBLE
                for (v in binding.widgetList.iterator()) {
                    if (v is WidgetView) {
                        v.editMode = false
                        v.layoutTransition = ChangingLayoutTransition()
                    }
                }
                binding.fabEditWidget.apply {
                    setIconResource(R.drawable.ic_edit)
                    setText(R.string.menu_edit_widgets)
                    setOnClickListener {
                        viewModel.setEditMode(true)
                    }
                }
            }
        }

        context.lifecycleScope.launch {
            context.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                widgetHost.startListening()
                try {
                    awaitCancellation()
                } finally {
                    // TODO: find out why there is a NPE thrown sometimes
                    try {
                        widgetHost.stopListening()
                    } catch (e: NullPointerException) {
                        CrashReporter.logException(e)
                    }
                }
            }
        }

        binding.fabEditWidget.setOnClickListener {
            viewModel.setEditMode(true)
        }
    }

    fun setClockWidgetHeight(height: Int) {
        val params = binding.clockWidget.layoutParams
        params.height = height
        binding.clockWidget.layoutParams = params
    }


    private fun initWidgets() {
        binding.widgetList.removeAllViews()
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = (8 * dp).roundToInt()
        for (w in widgets) {
            val view = WidgetView(context)
            view.layoutTransition = ChangingLayoutTransition()
            view.layoutParams = params
            if (view.setWidget(w, widgetHost)) {
                binding.widgetList.addDragView(view, view.getDragHandle())
                view.onRemove = {
                    OneShotLayoutTransition.run(binding.widgetList)
                    binding.widgetList.removeDragView(view)
                    removeWidget(view.widget)
                }
                view.onResizeModeChange = {
                    OneShotLayoutTransition.run(binding.widgetList)
                }
            }
        }

        binding.widgetList.setOnViewSwapListener { _, firstPosition, _, secondPosition ->
            Collections.swap(widgets, firstPosition, secondPosition)
        }
    }


    @SuppressLint("CheckResult")
    private fun addWidget() {
        val usedWidgets = widgets.filter { it.type == WidgetType.INTERNAL }.map { it.data }
        val internalWidgets =
            viewModel.getInternalWidgets().filter { !usedWidgets.contains(it.data) }
        if (internalWidgets.isNotEmpty()) {
            MaterialDialog(context).show {
                title(R.string.widget_add_widget)
                listItems(items = internalWidgets.map { it.label }) { dialog, index, _ ->
                    val widget = internalWidgets[index]
                    val view = WidgetView(this@WidgetsView.context)
                    val params = LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = (8 * dp).roundToInt()
                    view.layoutParams = params
                    if (view.setWidget(widget, widgetHost)) {
                        view.editMode = true
                        binding.widgetList.addDragView(view, view.getDragHandle())
                        view.onRemove = {
                            OneShotLayoutTransition.run(binding.widgetList)
                            OneShotLayoutTransition.run(this@WidgetsView)
                            binding.widgetList.removeDragView(view)
                            removeWidget(view.widget)
                        }
                        view.onResizeModeChange = {
                            OneShotLayoutTransition.run(binding.widgetList)
                            OneShotLayoutTransition.run(this@WidgetsView)
                        }
                        widgets.add(widget)
                    }
                    dialog.dismiss()
                }
                @Suppress("DEPRECATION") // I don't care that neutral buttons are discouraged.
                neutralButton(R.string.widget_add_external) {
                    val appWidgetId = widgetHost.allocateAppWidgetId()
                    val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                    pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    pickWidgetLauncher.launch(pickIntent)
                    it.dismiss()
                }
            }
        } else {
            val appWidgetId = widgetHost.allocateAppWidgetId()
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            pickWidgetLauncher.launch(pickIntent)
        }
    }

    private fun removeWidget(widget: Widget?) {
        widget ?: return
        widgets.remove(widget)
        val id = widget.data.toIntOrNull() ?: return
        widgetHost.deleteAppWidgetId(id)
    }

    private fun bindAppWidget(data: Intent) {
        val widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId == -1) return
        val appWidget = AppWidgetManager.getInstance(context)
            .getAppWidgetInfo(widgetId) ?: return
        val widget = Widget(
            type = WidgetType.THIRD_PARTY,
            data = widgetId.toString(),
            height = appWidget.minHeight
        )
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = (8 * dp).roundToInt()
        val view = WidgetView(context)
        view.layoutParams = params
        if (view.setWidget(widget, widgetHost)) {
            view.editMode = true

            binding.widgetList.addDragView(view, view.getDragHandle())
            view.onRemove = {
                OneShotLayoutTransition.run(binding.widgetList)
                OneShotLayoutTransition.run(this)
                binding.widgetList.removeDragView(view)
                removeWidget(view.widget)
            }
            view.onResizeModeChange = {
                OneShotLayoutTransition.run(binding.widgetList)
                OneShotLayoutTransition.run(this)
            }
            widgets.add(widget)
        }
    }

}