package de.mm20.launcher2.ui.launcher.widgets.picker

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.theme.LauncherTheme

class PickAppWidgetActivity : BaseActivity() {

    private val viewModel by viewModels<PickAppWidgetVM>()

    private lateinit var widgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHost = AppWidgetHost(this, 44203)
        appWidgetManager = AppWidgetManager.getInstance(this)

        val availableWidgets = viewModel.getAvailableWidgets(this)
        setContent {
            LauncherTheme {
                ProvideSettings {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(stringResource(R.string.widget_add_widget))
                                },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowBack,
                                            contentDescription = stringResource(
                                                id = R.string.menu_back
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    ) {
                        val available by availableWidgets.observeAsState()
                        val widgets = available
                        if (widgets != null) {
                            AppWidgetList(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(it),
                                widgets = widgets,
                                onWidgetSelected = {
                                    selectAppWidget(it)
                                }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(it),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun selectAppWidget(widget: AppWidgetProviderInfo) {
        val appWidgetId = widgetHost.allocateAppWidgetId()
        bindAppWidget(widget, appWidgetId)
    }

    private fun bindAppWidget(widget: AppWidgetProviderInfo, appWidgetId: Int) {
        val canBind = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, widget.provider)
        Log.d("MM20", "Can bind: $canBind")
        if (canBind) {
            configureAppWidget(widget, appWidgetId)
        } else {
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, widget.provider)
                }, RequestCodeBind
            )
        }
    }

    private fun configureAppWidget(widget: AppWidgetProviderInfo, appWidgetId: Int) {
        if (widget.configure != null) {
            widgetHost.startAppWidgetConfigureActivityForResult(
                this,
                appWidgetId,
                0,
                RequestCodeConfigure,
                null
            )
        } else {
            finishWithResult(appWidgetId)
        }
    }

    @Deprecated("Deprecated in super class")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RequestCodeBind -> {
                val appWidgetId =
                    data?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: return
                if (resultCode == RESULT_OK) {
                    val widget = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    configureAppWidget(widget, appWidgetId)
                } else {
                    widgetHost.deleteAppWidgetId(appWidgetId)
                    cancel()
                }
            }
            RequestCodeConfigure -> {
                val appWidgetId =
                    data?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: return cancel()
                if (resultCode == RESULT_OK) {
                    finishWithResult(appWidgetId)
                } else {
                    widgetHost.deleteAppWidgetId(appWidgetId)
                    cancel()
                }
            }
        }
    }

    private fun finishWithResult(widgetId: Int) {
        val data = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        const val RequestCodeConfigure = 1
        const val RequestCodeBind = 2
    }
}